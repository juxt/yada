;; Copyright © 2015, JUXT LTD.

(ns yada.security
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [clojure.data.codec.base64 :as base64]
   [yada.authorization :as authorization]))

(defmulti verify
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}] scheme) :default ::default)

(defmethod verify "Basic" [ctx {:keys [verify]}]

  (let [auth (get-in ctx [:request :headers "authorization"])
        cred (and auth (apply str (map char (base64/decode (.getBytes (last (re-find #"^Basic (.*)$" auth)))))))]
    (when cred
      (let [[user password] (str/split (str cred) #":" 2)]
        (verify [user password])))))

#_(defmethod verify :cookie [ctx {:keys [verify cookie]}]

  (get-in ctx [:cookies cookie])

  )

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
  (warnf "No installed support for the following scheme: %s" scheme)
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
     ~@body))

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

(defn authenticate [ctx]
  (when-not-cors-preflight ctx
    ;; Note that a response can have multiple challenges, one for each realm.
    (reduce
     (fn [ctx [realm {:keys [authentication-schemes]}]]
       ;; Currently we take the credentials of the first scheme that
       ;; returns them.  We also encourage scheme provides to return
       ;; truthy (e.g. {}) if the credentials have been specified (the
       ;; correct request header or cookie has been used) but are
       ;; invalid. This is to distinguish between (i) authentication
       ;; credentials being supplied and valid, (ii) supplied and
       ;; invalid, (iii) not supplied.
       ;;
       ;; The upshot of this is that invalid basic auth creds are
       ;; accepted (on the first attempt), so if the user makes a
       ;; mistake typing them in, no re-attempts are allowed. It is
       ;; hard for yada to provide re-attempts to a human, because it
       ;; is designed to support other types of user-agent, where
       ;; re-attempt counting would not be desirable.
       ;;
       ;; The compromise is that basic auth has a single attempt and
       ;; we must find some better way of allowing humans to 'log out'
       ;; of basic auth via browser JS. If re-attempts are desirable,
       ;; then it is recommended to use a more sophisticated auth
       ;; scheme rather than Basic, which is really only for quick
       ;; prototypes and examples. I think this is a valid overall
       ;; compromise between the various trade-offs here.

       ;; In the future we may have a better design that can support
       ;; conjunctions and disjunctions across auth-schemes, in much
       ;; the same way we do for the built-in role-based
       ;; authorization.
       (let [authentication-schemes (call-fn-maybe authentication-schemes ctx)
             credentials (some (partial verify ctx) authentication-schemes)]

         (if credentials
           (assoc-in ctx [:authentication realm] credentials)
           (let [vs (filter some?
                            (for [{:keys [scheme]} authentication-schemes]
                              (when (string? scheme)
                                (format "%s realm=\"%s\"" scheme realm))))]
             (if (not-empty vs)
               (update-in ctx [:response :headers "www-authenticate"]
                          (fnil conj [])
                          (str/join ", " vs))
               ctx)))))

     ctx (get-in ctx [:resource :access-control :realms]))))

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
           (authorization/validate ctx credentials authorization))
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

        (:allow-credentials access-control)
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
      https? (assoc-in [:response :headers "content-security-policy"]
                       (get-in ctx [:content-security-policy] "default-src https: data: 'unsafe-inline' 'unsafe-eval'"))
      true (assoc-in [:response :headers "x-frame-options"]
                     (get ctx :x-frame-options "SAMEORIGIN"))
      true (assoc-in [:response :headers "x-xss-protection"]
                     (get ctx :xss-protection "1; mode=block"))
      true (assoc-in [:response :headers "x-content-type-options"]
                     "nosniff"))))
