;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [bidi.bidi :refer (Matched succeed)]
   [bidi.ring :refer (Ring)]
   [manifold.deferred :as d]
   [manifold.stream :refer (->source transform)]
   [hiccup.core :refer (html h)]
   [schema.core :as s]
   [schema.coerce :refer (coercer string-coercion-matcher) :as sc]
   [schema.utils :refer (error? error-val)]
   [yada.resource :as p]
   [yada.state :as yst]
   [yada.conneg :refer (best-allowed-content-type)]
   [yada.coerce :refer (coercion-matcher)]
   [yada.representation :as rep]
   [ring.util.time :refer (parse-date format-date)]
   [ring.middleware.basic-authentication :as ba]
   [ring.middleware.params :refer (params-request)]
   [ring.swagger.schema :as rs]
   [ring.swagger.coerce :as rc]
   [ring.util.codec :refer (form-decode)]
   [ring.util.request :refer (character-encoding urlencoded-form? content-type)]
   [clojure.tools.logging :refer :all]
   [clojure.set :as set]
   [clojure.core.async :as a]
   [clojure.pprint :refer (pprint)]
   [clojure.walk :refer (keywordize-keys)]
   [cheshire.core :as json]
   )
  (:import
   (java.util Date)
   (manifold.deferred IDeferred Deferrable)
   (clojure.lang IPending)
   (java.util.concurrent Future)
   (schema.utils ValidationError ErrorContainer)))

(def k-bidi-match-context :bidi/match-context)

(defprotocol YadaInvokeable
  (invoke-with-initial-context [_ req ctx]
    "Invoke the yada handler with some initial context entries"))

;; Yada returns instances of YadaHandler, which allows yada to integrate
;; with bidi's matching-context.
(defrecord YadaHandler [delegate]
  clojure.lang.IFn
  (invoke [_ req] (delegate req {}))
  YadaInvokeable
  (invoke-with-initial-context [this req ctx] (delegate req ctx))
  Matched
  (resolve-handler [this m]
    (succeed this m))
  (unresolve-handler [this m]
    (when (= this (:handler m)) ""))
  Ring
  (request [this req m]
    (delegate req {k-bidi-match-context m})))

;; "It is better to have 100 functions operate on one data structure
;; than 10 functions on 10 data structures." — Alan Perlis

(defn- not* [[result m]]
  [(not result) m])

(defmacro nonblocking-exit-when*
  "Short-circuit exit a d/chain with an error if expr evaluates to
  truthy. To avoid blocking the request thread, the callback can return
  a deferred value."
  [callback expr status]
  `(fn [x#]
     (let [[b# m#] (when (some? ~callback) ; guard for performance
                   ~expr)]
       ;; Exit, intended to be caught with a d/catch
       (if b#
         (d/error-deferred (ex-info "" (merge {:status ~status
                                               ::http-response true} m#)))
         x#))))

(defmacro nonblocking-exit-when
  [callback expr status]
  `(nonblocking-exit-when* ~callback (deref (d/chain ~expr)) ~status))

(defmacro nonblocking-exit-when-not
  [callback expr status]
  `(nonblocking-exit-when* ~callback (deref (d/chain ~expr not*)) ~status))

(defmacro exit-when [expr status]
  `(fn [x#]
     (let [[b# m#] ~expr]
       (if b#
         (d/error-deferred (ex-info "" (merge {:status ~status
                                               ::http-response true}
                                              m#)))
         x#))))

(defmacro exit-when-not [expr status]
  `(exit-when (not* ~expr) ~status))

(defn spyctx [ctx]
  (debugf "Context is %s" ctx)
  ctx)

(defrecord NoAuthorizationSpecified []
  p/Resource
  (authorize [b ctx] true)
  (authorization [_] nil))

(def realms-xf
  (comp
   (filter (comp (partial = :basic) :type))
   (map :realm)))

(defn as-sequential [s]
  (if (sequential? s) s [s]))

(defn cors [allow-origin]
  (fn [ctx]
    (if-let [origin (p/allow-origin allow-origin ctx)]
      (update-in ctx [:response :headers]
                 merge {"access-control-allow-origin"
                        origin
                        "access-control-expose-headers"
                        (apply str
                               (interpose ", " ["Server" "Date" "Content-Length" "Access-Control-Allow-Origin"]))})
      ctx)))

(defn return-response [status headers]
  (fn [ctx]
    (merge
     {:status (or (get-in ctx [:response :status])
                  (p/status status ctx)
                  200)
      :headers (merge
                (get-in ctx [:response :headers])
                (p/headers headers ctx))
      ;; TODO :status and :headers should be implemented like this in all cases
      :body (get-in ctx [:response :body])})))

(defn exists-flow [method state req status headers body post allow-origin]
  (fn [ctx]
    (case method
      (:get :head)
      (d/chain
       ctx

       ;; Conditional request
       (fn [ctx]
         (infof "Checking for conditional request: %s" (:headers req))
         (infof "State is %s" state)
         (if-let [last-modified (yst/last-modified state)]

           (do
             (infof "AAA: last-modified: %s" last-modified)
             (if-let [if-modified-since (some-> req
                                                (get-in [:headers "if-modified-since"])
                                                parse-date)]
               (let [last-modified (if (d/deferrable? last-modified) @last-modified last-modified)]

                 (infof "last-mod: %s, if-mod-since: %s" last-modified if-modified-since)
                 (if (<=
                      (.getTime last-modified)
                      (.getTime if-modified-since))

                   ;; exit with 304
                   (d/error-deferred
                    (ex-info "" (merge {:status 304
                                        ::http-response true}
                                       ctx)))

                   (assoc-in ctx [:response :headers "last-modified"] (format-date last-modified))

                   ))

               (do
                 (infof "No if-modified-since header: %s" (:headers req))
                 (or
                  (some->> (if (d/deferrable? last-modified) @last-modified last-modified)
                           format-date
                           (assoc-in ctx [:response :headers "last-modified"]))
                  ctx))))
           (do
             (infof "yst/last-modified is nil")
             ctx)))

       ;; OK, let's pick the resource's state
       (fn [ctx]
         (d/chain
          state                         ; could be nil
          #(p/state % ctx)
          #(assoc-in ctx [:resource :state] %)))

       ;; Create body
       (fn [ctx]
         (let [state (get-in ctx [:resource :state])
               content-type
               (or (get-in ctx [:response :content-type])
                   ;; It's possible another callback has set the content-type header
                   (get-in headers ["content-type"])
                   (when-not body
                     ;; Hang on, we should have done content neg by now
                     (rep/content-type-default state)))
               content-length (rep/content-length state)]

           (d/chain

            ;; Determine body
            (cond
              body (p/body body ctx)
              state state         ; the state here can still be deferred
              )

            ;; serialize to representation (an existing string will be left intact)
            (fn [state]
              (rep/content state content-type))

            ;; on nil, compose default result (if in dev)
            #_(fn [x] (if x x (rep/content nil content-type)))

            #(assoc-in ctx [:response :body] %)
            #(if content-type
               (update-in % [:response :headers] assoc "content-type" content-type)
               %
               )

            #(if content-length
               (update-in % [:response :headers] assoc "content-length" content-length)
               %
               )))))

      :post
      (d/chain
       ctx

       (fn [ctx]
         (when-let [etag (get-in req [:headers "if-match"])]
           (when (not= etag (get-in ctx [:resource :etag]))
             (throw
              (ex-info "Precondition failed"
                       {:status 412
                        ::http-response true}))))
         ctx)

       (fn [ctx]
         ;; TODO: what if error?
         (p/interpret-post-result (p/post post ctx) ctx))

       (fn [ctx]
         (-> ctx
             (assoc-in [:response :status] 200))))

      :put
      (d/chain
       ctx

       (fn [ctx]
         (when-let [etag (get-in req [:headers "if-match"])]
           (when (not= etag (get-in ctx [:resource :etag]))
             (throw
              (ex-info "Precondition failed"
                       {:status 412
                        ::http-response true}))))
         ctx)

       (fn [ctx]
         (assoc-in ctx [:response :status] 204)))

      :options
      (d/chain
       ctx

       (fn [ctx]
         (if-let [origin (p/allow-origin allow-origin ctx)]
           (update-in ctx [:response :headers]
                      merge {"access-control-allow-origin"
                             origin
                             "access-control-allow-methods"
                             (apply str
                                    (interpose ", " ["GET" "POST" "PUT" "DELETE"]))})
           ctx)))

      (throw (ex-info "Unknown method"
                      {:status 501
                       :http-response true})))))

(defn yada*
  [{:keys
    [service-available?                 ; async-supported
     known-method?
     request-uri-too-long?

     ;; The allowed? callback will contain the entire resource, the callback must
     ;; therefore extract the OAuth2 scopes, or whatever is needed to
     ;; authorize the request.
     ;; allowed?

     status                             ; async-supported
     headers                            ; async-supported

     ;;resource                           ; async-supported
     state                              ; async-supported
     body                               ; async-supported

     ;; Security
     authorization                      ; async-supported
     security

     ;; Actions
     put                                ; async-supported
     post                               ; async-supported
     delete                             ; async-supported
     patch                              ; async-supported

     produces
     parameters

     ;; CORS
     allow-origin

     ] ;; :or {resource {}}

    :as resource-map

    :or {authorization (NoAuthorizationSpecified.)}
    } ]

  ;; We use this let binding to deduce the values of resource-map
  ;; entries that have not been given, based on the values of entries
  ;; that have. This approach makes it possible for developers to leave
  ;; out entries that are implied by the other entries. For example, if a body has been specified, we resource

  (let [security (as-sequential security)]

    (->YadaHandler

     (fn [req ctx]

       (let [method (:request-method req)]

         (-> ctx
             (merge
              {:request
               (assoc req
                      ;; We assoc in a 'delayed' body to the context's
                      ;; version of the request. The first caller to
                      ;; deref causes the slurp (which to do ahead of
                      ;; time might be unnecessarily wasteful).
                      :body (delay
                             (slurp (:body req) :encoding (or (character-encoding req) "UTF-8"))))
               :resource-map resource-map})

             (d/chain
              (nonblocking-exit-when-not service-available? (p/service-available? service-available?) 503)
              (exit-when-not (p/known-method? known-method? method) 501)
              (exit-when (p/request-uri-too-long? request-uri-too-long? (:uri req)) 414)

              ;; Method Not Allowed
              (fn [ctx]
                (if-not
                    (or
                     (case method
                       :get (or state body)
                       :put put
                       :post post
                       :options (or allow-origin)
                       nil)
                     )
                  (do
                    (warnf "Method not allowed %s" method)
                    (d/error-deferred
                     (ex-info (format "Method: %s" method)
                              {:status 405
                               ::http-response true})))
                  ctx))

              ;; Malformed
              (fn [ctx]
                (let [keywordize (fn [m] (into {} (for [[k v] m] [(keyword k) v])))
                      parameters
                      {:path
                       (when-let [schema (get-in parameters [method :path])]
                         (rs/coerce schema (:route-params req) :query))

                       :query
                       (when-let [schema (get-in parameters [method :query])]
                         (rs/coerce schema (-> req params-request :query-params keywordize) :query))

                       :body
                       (when-let [schema (get-in parameters [method :body])]
                         (let [body (-> ctx :request :body deref)]
                           (rep/decode-representation body (content-type req) schema)))

                       :form
                       (when-let [schema (get-in parameters [method :form])]
                         (when (urlencoded-form? req)
                           (let [fp (keywordize-keys
                                     (form-decode (-> ctx :request :body deref)
                                                  (character-encoding req)))]
                             (rs/coerce schema fp :json))))

                       :header
                       (when-let [schema (get-in parameters [method :header])]
                         (let [params (select-keys (-> req :headers keywordize-keys) (keys schema))]
                           (rs/coerce schema params :query)))}]

                  (let [errors (filter (comp error? second) parameters)]

                    (if (not-empty errors)
                      (d/error-deferred (ex-info "" {:status 400
                                                     :body errors
                                                     ::http-response true}))
                      (let [body (:body parameters)
                            params (merge (apply merge (vals (dissoc parameters :body)))
                                          (when body {:body body}))]
                        (cond-> ctx
                          (not-empty params) (assoc :parameters params)
                          ;; Although body is included in params (above)
                          ;; we might also include it in the context directly.
                          ;; body (assoc :body (:body parameters))
                          ))))))

              ;; Authentication
              (fn [ctx]
                (cond-> ctx
                  (not-empty (filter (comp (partial = :basic) :type) security))
                  (assoc :authentication (:basic-authentication (ba/basic-authentication-request req (fn [user password] {:user user :password password}))))))

              ;; Authorization
              (fn [ctx]
                (if-let [res (p/authorize authorization ctx)]
                  (if (= res :not-authorized)
                    (d/error-deferred
                     (ex-info ""
                              (merge
                               {:status 401 ::http-response true}
                               (when-let [basic-realm (first (sequence realms-xf security))]
                                 {:headers {"www-authenticate" (format "Basic realm=\"%s\"" basic-realm)}}))
                              ))
                    (if-let [auth (p/authorization res)]
                      (assoc ctx :authorization auth)
                      ctx))
                  (d/error-deferred (ex-info "" {:status 403
                                                 ::http-response true}))))

              ;; TODO Not implemented (if unknown Content-* header)

              ;; TODO Unsupported media type

              ;; TODO Request entity too large - shouldn't we do this later,
              ;; when we determine we actually need to read the request body?

              ;; TODO OPTIONS

              ;; Content-negotiation - partly done here to throw back to the client any errors
              #(let [produces (or (p/produces produces)
                                  (p/produces-from-body body))]
                 (if-let [content-type
                          (best-allowed-content-type
                           (or (get-in req [:headers "accept"]) "*/*")
                           produces
                           )]
                   (assoc-in % [:response :content-type] content-type)
                   (if produces
                     ;; If there is a produces specification, but not
                     ;; matched content-type, it's a 406.
                     (d/error-deferred (ex-info "" {:status 406
                                                    ::http-response true}))
                     ;; Otherwise return the context unchanged
                     %)))

              ;; Split the flow based on the existence of the resource
              (fn [ctx]

                (cond
                  ;; 'Exists' flow
                  (or state body (#{:post :put} method))
                  (d/chain
                   ctx
                   ;; Not sure we should use exists-flow for CORS pre-flight requests, should handle further above
                   (exists-flow method state req status headers body post allow-origin)
                   (cors allow-origin)
                   (return-response status headers))

                  ;; 'Not exists' flow
                  :otherwise
                  (d/chain ctx (constantly {:status 404})))))

             ;; Handle exits
             (d/catch clojure.lang.ExceptionInfo
                 #(let [data (ex-data %)]
                    (if (::http-response data)
                      data
                      (throw (ex-info "Internal Server Error (ex-info)" data %))
                      #_{:status 500
                         :body (format "Internal Server Error: %s" (pr-str data))})))

             (d/catch #(identity
                        (throw (ex-info "Internal Server Error" {:request req} %))
                        #_{:status 500 :body
                           (html
                            [:body
                             [:h1 "Internal Server Error"]
                             [:p (str %)]
                             [:pre (with-out-str (apply str (interpose "\n" (seq (.getStackTrace %)))))]
                             ])}))))))))


;; TODO: pets should return resource-metadata with a (possibly deferred) model

;; handle-method-not-allowed 405 "Method not allowed."

;; This is OK
;; ((api-handler api) (mock/request :get "/persons"))

;; This is should yield 405 "Method not allowed."
;; ((api-handler api) (mock/request :get "/persons"))

;; List of interesting things to do

;; There should be a general handler that does the right thing
;; wrt. available methods (Method Not Allowed) and calls out to
;; callbacks accordingly. Perhaps there's no sense in EVERYTHING being
;; overridable, as with Liberator. It should hard-code the things that
;; don't make sense to override, and use hooks for the rest.

;; Resource existence is most important - and not covered by swagger, so it's a key hook.

;; Return deferreds, if necessary, if the computation is length to compute (e.g. for exists? checks)

;; CORS support: build this in, make allow-origin first-class, which headers should be allowed should be a hook (with default)


(defn yada [& args]
  (if (keyword? (first args))
    (yada* (into {} (map vec (partition 2 args))))
    (yada* (first args))))
