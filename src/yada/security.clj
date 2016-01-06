;; Copyright © 2015, JUXT LTD.

(ns yada.security
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [clojure.data.codec.base64 :as base64]
   [ring.middleware.basic-authentication :as ba]))

(defmulti authenticate-with-scheme
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}]
    scheme))

(defmethod authenticate-with-scheme "Basic"
  [ctx {:keys [authenticator]}]
  (:basic-authentication
   (ba/basic-authentication-request
    (:request ctx)
    (fn [user password]
      (authenticator [user password])))))

;; A nil scheme is simply one that does not use any of the built-in
;; algorithms for IANA registered auth-schemes at
;; http://www.iana.org/assignments/http-authschemes. The authenticator
;; must therefore take the full context and do all the work to
;; authenticate the user from it.
(defmethod authenticate-with-scheme nil
  [ctx {:keys [authenticator]}]
  (when authenticator
    (authenticator ctx)))

(defmethod authenticate-with-scheme :default
  [ctx {:keys [scheme]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). This is technically a server issue but
  ;; we recover and add a warning in the logs.
  (warnf "No installed support for the following scheme: %s" scheme)
  nil)

(defn authenticate [ctx]
  ;; If [:access-control :allow-origin] exists at all, don't block an OPTIONS pre-flight request
  (if (and (= (:method ctx) :options)
           (some-> ctx :resource :cors :allow-origin))
    ;; Let through without authentication, CORS OPTIONS is
    ;; incompatible with authorization, since it is forbidden to send
    ;; credentials in a pre-flight request.
    ctx

    ;; Note that a response can have multiple challenges, one for each realm.
    (reduce
     (fn [ctx [realm {:keys [schemes]}]]
       (let [credentials (some (partial authenticate-with-scheme ctx) schemes)]
         (cond-> ctx
           credentials (assoc-in [:authentication realm] credentials)
           credentials (update-in [:authentication :combined-roles]
                                  (fnil set/union #{})
                                  (set (for [role (:roles credentials)]
                                         {:realm realm :role role})))
           (not credentials) (update-in [:response :headers "www-authenticate"]
                                        (fnil conj [])
                                        (str/join ", "
                                                  (filter some?
                                                          (for [{:keys [scheme]} schemes]
                                                            (when scheme
                                                              (format "%s realm=\"%s\"" scheme realm)))))))))
     ctx (get-in ctx [:resource :authentication :realms]))))

(defn authorize
  "Given an authenticated user in the context, and the resource
  properties in :properites, check that the user is authorized to do
  what they are about to do. At this point the user is already
  authenticated and roles determined, if it is possible to do
  so (RBAC), and the resource's properties (attributes) have been
  loaded to make ABAC schemes also possible."
  [ctx]

  ;; For each realm that our roles are defined in.
  
  (let [required-roles (some-> ctx :resource :methods (get (:method ctx)) :restrict)]
    (if required-roles
      (let [assigned-roles (get-in ctx [:authentication :combined-roles])
            accessing-roles (set/intersection (set required-roles) assigned-roles)]
        (if (not-empty accessing-roles) ; disjunction
          ctx ;; allow, perhaps log the accessing-roles in the context the 
          (d/error-deferred
           (ex-info "Failed authorization check"
                    {:status 401
                     ;; But allow WWW-Authenticate header in error
                     :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])}))))
      ;; Otherwise, pass through
      ctx)))

(defn call-fn-maybe [x ctx]
  (when x
    (if (fn? x) (x ctx) x)))

(defn to-header [v]
  (if (coll? v)
    (apply str (interpose ", " v))
    (str v)))

(defn access-control-headers [ctx]
  (if-let [origin (get-in ctx [:request :headers "origin"])]
    (let [cors (get-in ctx [:resource :cors])
          ;; We can only report one origin, so let's work that out
          allow-origin (let [s (call-fn-maybe (:allow-origin cors) ctx)]
                         (cond
                           (= s "*") "*"
                           (string? s) s
                           ;; Allow function to return a set
                           (ifn? s) (or (s origin)
                                        (s "*"))))]
      
      (cond-> ctx
        allow-origin
        (assoc-in [:response :headers "access-control-allow-origin"] allow-origin)

        (:allow-credentials cors)
        (assoc-in [:response :headers "access-control-allow-credentials"]
                  (to-header (:allow-credentials cors)))

        (:expose-headers cors)
        (assoc-in [:response :headers "access-control-expose-headers"]
                  (to-header (call-fn-maybe (:expose-headers cors) ctx)))

        (:max-age cors)
        (assoc-in [:response :headers "access-control-max-age"]
                  (to-header (call-fn-maybe (:max-age cors) ctx)))

        (:allow-methods cors)
        (assoc-in [:response :headers "access-control-allow-methods"]
                  (to-header (map (comp str/upper-case name) (call-fn-maybe (:allow-methods cors) ctx))))

        (:allow-headers cors)
        (assoc-in [:response :headers "access-control-allow-headers"]
                  (to-header (call-fn-maybe (:allow-headers cors) ctx)))))

    ;; Otherwise
    ctx))
