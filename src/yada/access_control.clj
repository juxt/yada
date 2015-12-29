;; Copyright © 2015, JUXT LTD.

(ns yada.access-control
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [clojure.tools.logging :refer :all]
   [clojure.data.codec.base64 :as base64]
   [ring.middleware.basic-authentication :as ba]))

(defmulti authenticate-with-scheme
  ""
  (fn [ctx {:keys [scheme]}] scheme))

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
  [ctx {:keys [authenticator]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). Arguably this is a 400.
  nil)

(defn authenticate [ctx]
  ;; If [:access-control :allow-origin] exists at all, don't block an OPTIONS pre-flight request
  (if (and (= (:method ctx) :options)
           (-> ctx :handler :resource :access-control :allow-origin))
    ctx                           ; let through without authentication

    (if-let [auth (get-in ctx [:handler :resource :access-control :authentication])]
      (if-let [realms (:realms auth)]
        ;; Only supports one realm currently, TODO: support multiple realms as per spec. 7235
        (let [[realm {:keys [schemes]}] (first realms)]
          
          (if-let [user (some (partial authenticate-with-scheme ctx) schemes)]
            (assoc ctx :user user)

            ;; Otherwise, if no authorization header, send a
            ;; www-authenticate header back.
            (d/error-deferred
             (ex-info "" {:status 401
                          :headers {"www-authenticate"
                                    (apply str (interpose ", "
                                                          (for [{:keys [scheme ]} schemes]
                                                            (format "%s realm=\"%s\"" scheme realm))))}}))))
        ;; if no realms
        (d/error-deferred (ex-info "" {:status 401
                                       :headers {"www-authenticate" "Basic realm=\"simple\""}})))
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
    (let [access-control (get-in ctx [:handler :resource :access-control])
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
                  (to-header (call-fn-maybe (:allow-methods access-control) ctx)))

        (:allow-headers access-control)
        (assoc-in [:response :headers "access-control-allow-headers"]
                  (to-header (call-fn-maybe (:allow-headers access-control) ctx)))))

    ;; Otherwise
    ctx))
