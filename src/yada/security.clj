;; Copyright © 2014-2017, JUXT LTD.

(ns yada.security
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [manifold.deferred :as d]
   [yada.authorization :as authorization]))

(defmulti verify
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}] scheme) :default ::default)

(defmulti scheme-default-parameters
  "Multimethod that allows auth-schemes to have default parameters"
  (fn [ctx {:keys [scheme]}] scheme) :default ::default)

(defmethod scheme-default-parameters ::default [ctx {:keys [realm]}]
  {:realm realm})

;; A nil scheme is simply one that does not use any of the built-in
;; algorithms for IANA registered auth-schemes at
;; http://www.iana.org/assignments/http-authschemes. The verify
;; entry must therefore take the full context and do all the work to
;; verify the user from it.
(defmethod verify nil
  [ctx {:keys [verify]}]
  (when verify
    (verify ctx)))

(defmethod verify ::default
  [ctx {:keys [scheme]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). This is technically a server issue but
  ;; we recover and add a warning in the logs.
  (log/warnf "No installed support for the following scheme: %s" scheme)
  nil)

(defn cors-preflight?
  "Is the method OPTIONS and does the resource accept requests from
  other origins? This is important because we can't block pre-flight
  requests– the CORS spec. doesn't allow us to."
  [ctx]
  (and (= (:method ctx) :options)
       (some-> ctx :resource :access-control :allow-origin)))

(defmacro when-not-cors-preflight [ctx & body]
  `(if (cors-preflight? ~ctx)
     ~ctx
     (do ~@body)))

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

(defn challenge [scheme params]
  (format "%s %s" scheme (str/join ", " (for [[k v] params] (format "%s=\"%s\"" (name k) v)))))

(defn www-authenticate
  "Takes a collection of realm results. A relevant realm result has a
  non-truty :authenticated? value. It also has a collection
  of :auth-schemes that should be presented to the user-agent for it
  to select which it deems the most secure."
  [ctx realm-results]
  (let [challenges
             (for [{:keys [authenticated? scheme realm]} (mapcat :auth-schemes realm-results)
                   :let [s (:scheme scheme)
                         params (call-fn-maybe (get scheme :params {}) ctx)]
                   :when (not authenticated?)
                   :when (string? s)]
               (challenge s (let [params (scheme-default-parameters ctx (assoc scheme :realm realm))]
                              (if (and (find params :realm) (nil? (:realm params)))
                                (dissoc params :realm)
                                (assoc params :realm realm)))))]
    (when (not-empty challenges)
      (str/join", " challenges))))

(defn authenticate [ctx]
  (when-not-cors-preflight ctx

    ;; Note that a response can have a challenges per realm. Each
    ;; challenge can refer to multiple auth-schemes.

    (d/chain

     (apply
      d/zip
      (map
       (fn [[realm {:keys [authentication-schemes]}]]

         (let [authentication-schemes (call-fn-maybe authentication-schemes ctx)]

           (if (empty? authentication-schemes)
             {:authenticated? false :realm realm}
             (d/chain
              (d/loop [acc []
                       [scheme & next-auth-schemes] authentication-schemes]

                ;; For each authentication scheme, call the
                ;; authenticate function can. If one doesn't exist,
                ;; then we dispatch to the verify multimethod.
                (if-let [creds
                         (cond
                           (:authenticate scheme) ((:authenticate scheme) ctx)
                           (:scheme scheme) (verify ctx (assoc scheme :realm realm))
                           :else
                           (throw (ex-info "Authentication scheme does not support authentication" {:scheme scheme})))]

                  {:authenticated? true :realm realm :scheme scheme :credentials creds}

                  (let [acc (conj acc {:scheme scheme :realm realm})]
                    (if next-auth-schemes
                      ;; Try another scheme
                      (d/recur acc next-auth-schemes)
                      ;; No schemes have authenticated
                      {:authenticated? false :realm realm :auth-schemes acc}))))))))

       (get-in ctx [:resource :access-control :realms])))

     (fn [realm-results]

       ;; TODO: Document in authentication section

       ;; Apply the credentials to the context
       (let [ctx
             (reduce
              (fn [ctx {:keys [authenticated? realm credentials] :as result}]

                (cond-> ctx
                  authenticated? (assoc-in [:authentication realm] credentials))

                ) ctx realm-results)

             www-auth (www-authenticate ctx realm-results)]

         (cond-> ctx
           www-auth (assoc-in [:response :headers "www-authenticate"] www-auth))
         )))))

(defn authorize
  "Given a verified user in the context, and the resource properties
  in :properites, check that the user is authorized to do what they
  are about to do. At this point the user is already verified and
  roles determined, if it is possible to do so (RBAC), and the
  resource's properties (attributes) have been loaded to make ABAC
  schemes also possible."
  [ctx]
  (when-not-cors-preflight ctx
    (reduce
     (fn [ctx [realm realm-val]]
       (if-let [authorization (:authorization realm-val)]
         (let [credentials (get-in ctx [:authentication realm])]
           (let [validation
                 (authorization/validate ctx credentials authorization)]
             (if (or (nil? validation) (false? validation))
               (if credentials
                 (throw
                  (ex-info "Forbidden"
                           {:status 403 ; or 404 to keep the resource hidden
                            ;; But allow WWW-Authenticate header in error
                            :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])}))
                 (throw
                  (ex-info "No authorization provided"
                           {:status 401 ; or 404 to keep the resource hidden
                            ;; But allow WWW-Authenticate header in error
                            :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])})))
               validation)))
         ctx))
     ctx (get-in ctx [:resource :access-control :realms]))))


(defn to-header [v]
  (if (coll? v)
    (apply str (interpose ", " v))
    (str v)))

(defn access-control-headers [ctx]
  (if-let [origin (get-in ctx [:request :headers "origin"])]
    (let [access-control (get-in ctx [:resource :access-control])
          ;; We can only report one origin, so let's work that out
          allow-origin (let [s (call-fn-maybe (:allow-origin access-control) ctx)]
                         (cond
                           (= s "*") "*"
                           (string? s) s
                           ;; Allow function to return a set
                           (ifn? s) (or (s origin)
                                        (s "*"))))]

      (cond-> ctx
        allow-origin
        (assoc-in [:response :headers "access-control-allow-origin"] allow-origin)

        (contains? access-control :allow-credentials)
        (assoc-in [:response :headers "access-control-allow-credentials"]
                  (to-header (:allow-credentials access-control)))

        (:expose-headers access-control)
        (assoc-in [:response :headers "access-control-expose-headers"]
                  (to-header (call-fn-maybe (:expose-headers access-control) ctx)))

        (:max-age access-control)
        (assoc-in [:response :headers "access-control-max-age"]
                  (to-header (call-fn-maybe (:max-age access-control) ctx)))

        (:allow-methods access-control)
        (assoc-in [:response :headers "access-control-allow-methods"]
                  (to-header (map (comp str/upper-case name) (call-fn-maybe (:allow-methods access-control) ctx))))

        (:allow-headers access-control)
        (assoc-in [:response :headers "access-control-allow-headers"]
                  (to-header (call-fn-maybe (:allow-headers access-control) ctx)))))

    ;; Otherwise
    ctx))

(defn security-headers [ctx]
  (let [scheme (-> ctx :request :scheme)
        https? (= scheme :https)]

    (cond-> ctx
      https? (assoc-in [:response :headers "strict-transport-security"]
                       (format
                         "max-age=%s; includeSubdomains"
                         (get-in ctx [:strict-transport-security :max-age] 31536000)))
      (or https? (contains? (:resource ctx) :content-security-policy))
      (assoc-in [:response :headers "content-security-policy"]
                (get-in ctx [:resource :content-security-policy]
                        "default-src https: data: 'unsafe-inline' 'unsafe-eval'"))
      true (assoc-in [:response :headers "x-frame-options"]
                     (get-in ctx [:resource :x-frame-options] "SAMEORIGIN"))
      true (assoc-in [:response :headers "x-xss-protection"]
                     (get-in ctx [:resource :xss-protection] "1; mode=block"))
      true (assoc-in [:response :headers "x-content-type-options"]
                     "nosniff"))))
