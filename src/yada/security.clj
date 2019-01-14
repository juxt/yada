;; Copyright © 2014-2017, JUXT LTD.

(ns yada.security
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [yada.authorization :as authorization]
   [yada.syntax :as syn]
   [clojure.tools.logging :as log]
   [manifold.deferred :as d])
  (:import
   (yada.context Context)))

;; Deprecated
(defmulti verify
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}] scheme) :default ::default)

;; Deprecated
(defmethod verify "Basic" [ctx {:keys [verify]}]

  (let [auth (get-in ctx [:request :headers "authorization"])
        cred (and auth (apply str (map char (base64/decode (.getBytes ^String (last (re-find #"^Basic (.*)$" auth)))))))]
    (when cred
      (let [[user password] (str/split (str cred) #":" 2)]
        (verify [user password])))))

;; Deprecated
;; A nil scheme is simply one that does not use any of the built-in
;; algorithms for IANA registered auth-schemes at
;; http://www.iana.org/assignments/http-authschemes. The verify
;; entry must therefore take the full context and do all the work to
;; verify the user from it.
(defmethod verify nil
  [ctx {:keys [verify]}]
  (when verify
    (verify ctx)))

;; Deprecated
(defmethod verify ::default
  [ctx {:keys [scheme]}]
  ;; Scheme is not recognised by this server, we must return nil (to
  ;; move to the next scheme). This is technically a server issue but
  ;; we recover and add a warning in the logs.
  (warnf "No installed support for the following scheme: %s" scheme)
  nil)

(defmulti issue-challenge
  "Multimethod that allows new schemes to be added."
  (fn [ctx {:keys [scheme]}] scheme) :default ::default)

(defmethod issue-challenge ::default
  [ctx {:keys [scheme]}]
  nil)

(defn issue-challenge-with-custom [ctx auth-scheme]
  (if-let [f (:challenge auth-scheme)]
    (f ctx)
    (issue-challenge ctx auth-scheme)))

(defmulti preprocess-authorization-header
  "Pre-process the parsed authorization value according to the
  auth-scheme's semantics. Return nil if anything wrong with the
  (claimed) credentials."
  (fn [ctx {:keys [scheme] :as auth-scheme} credentials] scheme) :default ::default)

(defmethod preprocess-authorization-header ::default
  [ctx {:keys [scheme] :as auth-scheme} credentials]
  ;; Return the identity of credentials
  credentials)

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

(defn add-challenges [ctx]
  (let [auth-schemes (get-in ctx [:resource :authentication-schemes])
        challenges (keep
                    (fn [auth-scheme]
                      (issue-challenge-with-custom ctx auth-scheme))
                    auth-schemes)]
    (cond-> ctx
      (not-empty challenges)
      (update-in [:response :headers "www-authenticate"]
                 (fnil conj [])
                 (syn/format-challenges challenges)))))

(defprotocol AuthenticateResult
  (interpret-authenticate-result [result ctx auth-scheme]
    "Process the result to a call to an authenticate function. This
    assists in allowing some authenticate functions to return a
    context, where necessary, while allowing others to return
    nil (indicating authentication failure) "))

(defn assoc-credentials
  "Associate the given credentials with the request context. Custom
  authenticator functions that wish to return the ctx explicitly,
  should call this function to associate credentials that they have
  established."
  [ctx creds]
  (assoc ctx :credentials creds))

(defn assoc-auth-scheme
  "When an authentication has established credentials, it is useful that
  the authentication scheme places itself into the request context,
  along with any parameters, for other interceptors to see."
  [ctx auth-scheme]
  (assoc ctx :authentication-scheme auth-scheme))

(extend-protocol AuthenticateResult
  nil
  (interpret-authenticate-result [_ ctx auth-scheme]
    ;; Return the original context
    ctx)

  Context
  (interpret-authenticate-result [new-ctx ctx auth-scheme]
    ;; The dev knows that they're doing, respect that. Assume
    ;; credentials have already been associated with the request
    ;; context.
    (-> new-ctx
        (assoc-auth-scheme auth-scheme)))

  ;; TODO: Allow for an authenticate function to provide partial
  ;; credentials and a new challenge, as per RFC 7235 section 2.1.

  Object
  (interpret-authenticate-result [creds ctx auth-scheme]
    (-> ctx
        (assoc-auth-scheme auth-scheme)
        (assoc-credentials creds))))

(defn authenticate [ctx]
  (when-not-cors-preflight ctx
    (let [auth-schemes (call-fn-maybe (get-in ctx [:resource :authentication-schemes]) ctx)
          realms (get-in ctx [:resource :access-control :realms])]

      (cond
        auth-schemes

        ;; If there's an authorization header, find the first scheme
        ;; that matches.

        ;; From RFC 7235 section 2.1:
        ;;
        ;; > "Upon receipt of a request for a protected resource that
        ;; > omits credentials, contains invalid credentials (e.g., a
        ;; > bad password) or partial credentials (e.g., when the
        ;; > authentication scheme requires more than one round trip),
        ;; > an origin server SHOULD send a 401 (Unauthorized)
        ;; > response that contains a WWW-Authenticate header field
        ;; > with at least one (possibly new) challenge applicable to
        ;; > the requested resource."

        ;; The above indicates that the authenticate function MAY
        ;; return a new challenge.

        (if-let [authorization (get-in ctx [:request :headers "authorization"])]

          ;; Find the authentication-scheme for which this authorization refers, if any
          (let [claimed-credentials (syn/parse-credentials authorization)]

            (if-let [auth-scheme
                     (if
                         ;; This is unexpected but we should handle it anyway
                         (not= (::syn/type claimed-credentials) ::syn/credentials)
                         (do
                           ;; Log this and return nil
                           (log/infof "Bad authorization header value received: %s" authorization)
                           nil)

                         (some
                          (fn [candidate]
                            (when (= (::syn/auth-scheme claimed-credentials) (str/lower-case (:scheme candidate)))
                              candidate))
                          auth-schemes))]

              ;; Auth-scheme found. First, we allow the scheme to pre-process the credentials
              (let [claimed-credentials (preprocess-authorization-header ctx auth-scheme claimed-credentials)
                    ;; We call the authenticate function with 3 args:
                    ;; ctx, credentials (pre-processed) and the
                    ;; auth-scheme data (to provide access to any
                    ;; extra parameters)
                    res ((:authenticate auth-scheme) ctx claimed-credentials auth-scheme)]

                ;; Allow authenticate functions to return deferred
                ;; values
                (d/chain
                 res
                 (fn [res]
                   (let [ctx (interpret-authenticate-result res ctx auth-scheme)]
                     (cond-> ctx
                       ;; If there are no credentials as a result
                       ;; then add the challenges
                       (nil? (:credentials ctx)) add-challenges)))))

              ;; No auth-scheme found.
              (do
                (log/infof "Authorization credentials do not match any of the authentication-scheme challenges")
                (add-challenges ctx))))

          (add-challenges ctx)
          ;; No authorization attempted. Nothing to do here except set www-authenticate headers (challenges)
          ;; For all schemes, ask the scheme to create a 'challenge'
          )

        ;; Deprecated but included for backwards compatibility
        realms
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

         ctx realms)
        :else ctx))))

(defprotocol AuthorizationResult
  (interpret-authorize-result [result ctx]
    "Process the result to a call to an authorize function. This
    assists in allowing some authorize functions to return a
    context, where necessary, while allowing others to return
    nil (indicating authorization failure)"))

(extend-protocol AuthorizationResult
  nil
  (interpret-authorize-result [_ ctx]
    ;; Return the original context, with no authorization added
    ctx)

  Context
  (interpret-authorize-result [new-ctx ctx]
    ;; The dev knows that they're doing, respect that. Assume
    ;; authorization has already been associated with the request
    ;; context.
    new-ctx)

  Object
  (interpret-authorize-result [authorization ctx]
    (assoc ctx :authorization authorization)))

(defn default-authorize
  "The default authorize succeeds if there are no authentication schemes
  declared, or if there are any credentials established. This is
  considered the path of least surprise. Protected resources are
  protected in the absence of credentials rather than requiring an
  explicit :authorize function."
  [ctx creds _]
  (if (get-in ctx [:resource :authentication-schemes])
    creds
    true))

(defn authorize
  "Given a verified user in the context, and the resource properties
  in :properties, check that the user is authorized to do what they
  are about to do. At this point the user is already verified and
  roles determined, if it is possible to do so (RBAC), and the
  resource's properties (attributes) have been loaded to make ABAC
  schemes also possible."
  [ctx]
  (when-not-cors-preflight ctx

    (let [authorization (call-fn-maybe (get-in ctx [:resource :authorization]) ctx)
          realms (get-in ctx [:resource :access-control :realms])]

      (cond
        (not realms)
        ;; New branch
        (let [f (or (:authorize authorization) default-authorize)]
          (d/chain
           (f ctx (:credentials ctx) authorization)
           (fn [res]
             (interpret-authorize-result res ctx))
           (fn [ctx]
             (if (:authorization ctx)
               ctx
               (if (:credentials ctx)
                 (throw
                  (ex-info "Forbidden"
                           {:status 403 ; or 404 to keep the resource hidden
                            ;; But allow www-authenticate header in error
                            :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])}))
                 (throw
                  (ex-info "No authorization provided"
                           {:status 401 ; or 404 to keep the resource hidden
                            ;; But allow www-authenticate header in error
                            :headers (select-keys (-> ctx :response :headers) ["www-authenticate"])})))))))

        ;; This is the 'old' branch that is now deprecated and
        ;; sticking around to provide backwards compatibility.
        realms
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
         ctx (get-in ctx [:resource :access-control :realms]))

        :else
        ctx))))

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
