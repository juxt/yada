;; Copyright © 2015, JUXT LTD.

(ns yada.security
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [clojure.data.codec.base64 :as base64]
   [yada.authorization :refer [allowed?]]))

(defmulti authenticate-with-scheme
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}]
    scheme))

(defmethod authenticate-with-scheme "Basic"
  [ctx {:keys [authenticate]}]

  (let [auth (get-in ctx [:request :headers "authorization"])
        cred (and auth (apply str (map char (base64/decode (.getBytes (last (re-find #"^Basic (.*)$" auth)))))))]
    (when cred
      (let [[user password] (str/split (str cred) #":" 2)]
        (or
         (authenticate [user password])
         {})))))

;; A nil scheme is simply one that does not use any of the built-in
;; algorithms for IANA registered auth-schemes at
;; http://www.iana.org/assignments/http-authschemes. The authenticate
;; entry must therefore take the full context and do all the work to
;; authenticate the user from it.
(defmethod authenticate-with-scheme nil
  [ctx {:keys [authenticate]}]
  (when authenticate
    (authenticate ctx)))

(defmethod authenticate-with-scheme :default
  [ctx {:keys [scheme]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). This is technically a server issue but
  ;; we recover and add a warning in the logs.
  (warnf "No installed support for the following scheme: %s" scheme)
  nil)

(defn cors-preflight?
  "Is the method OPTIONS and does the resource accept requests from
  other origins?"
  [ctx]
  (and (= (:method ctx) :options)
       (some-> ctx :resource :access-control :allow-origin)))

(defmacro when-not-cors-preflight [ctx & body]
  `(if (cors-preflight? ~ctx)
     ~ctx
     ~@body))

;; We need to distinguish between authentication credentials being
;; supplied and valid, supplied and invalid, not supplied.

(defn authenticate [ctx]
  (when-not-cors-preflight ctx
    ;; Note that a response can have multiple challenges, one for each realm.
    (reduce
     (fn [ctx [realm {:keys [authentication-schemes]}]]
       (let [credentials (some (partial authenticate-with-scheme ctx) authentication-schemes)]
         (infof "credentials for realm %s, schemes %s are %s" realm authentication-schemes credentials)
         (cond-> ctx
           credentials (assoc-in [:authentication realm] credentials)
           (not credentials) (update-in [:response :headers "www-authenticate"]
                                        (fnil conj [])
                                        (str/join ", "
                                                  (filter some?
                                                          (for [{:keys [scheme]} authentication-schemes]
                                                            (when scheme
                                                              (format "%s realm=\"%s\"" scheme realm)))))))))
     ctx (get-in ctx [:resource :access-control :realms]))))

(defn authorize
  "Given an authenticated user in the context, and the resource
  properties in :properites, check that the user is authorized to do
  what they are about to do. At this point the user is already
  authenticated and roles determined, if it is possible to do
  so (RBAC), and the resource's properties (attributes) have been
  loaded to make ABAC schemes also possible."
  [ctx]
  (when-not-cors-preflight ctx
    (reduce
     (fn [ctx [realm spec]]
       (let [credentials (get-in ctx [:authentication realm])
             expr (get-in spec [:authorized-methods (:method ctx)])]
         (infof "authorize: credentials %s expr %s" credentials expr)
         (if (allowed? expr ctx realm)
           ctx
           (if credentials
             (d/error-deferred
              (ex-info "Forbidden"
                       {:status 403   ; or 404 to keep the resource hidden
                        ;; But allow WWW-Authenticate header in error
                        :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])}))
             (d/error-deferred
              (ex-info "No authorization provided"
                       {:status 401   ; or 404 to keep the resource hidden
                        ;; But allow WWW-Authenticate header in error
                        :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])}))))))
     ctx (get-in ctx [:resource :access-control :realms]))))

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

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
