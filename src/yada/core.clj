;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require [bidi.bidi :refer (Matched succeed)]
            [bidi.ring :refer (Ring)]
            [cheshire.core :as json]
            [clojure.core.async :as a]
            [clojure.pprint :refer (pprint)]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all :exclude [trace]]
            [clojure.walk :refer (keywordize-keys)]
            [hiccup.core :refer (html h)]
            [manifold.deferred :as d]
            [manifold.stream :refer (->source transform)]
            [ring.middleware.basic-authentication :as ba]
            [ring.middleware.params :refer (params-request)]
            [ring.swagger.coerce :as rc]
            [ring.swagger.schema :as rs]
            [ring.util.codec :refer (form-decode)]
            [ring.util.request :refer (character-encoding urlencoded-form? content-type)]
            [ring.util.time :refer (parse-date format-date)]
            [schema.coerce :refer (coercer string-coercion-matcher) :as sc]
            [schema.core :as s]
            [schema.utils :refer (error? error-val)]
            [yada.coerce :refer (coercion-matcher)]
            [yada.conneg :refer (best-allowed-content-type)]
            [yada.representation :as rep]
            [yada.resource :as p]
            [yada.util :refer (link)]
            [yada.state :as yst])
  (:import (clojure.lang IPending)
           (java.util Date)
           (java.util.concurrent Future)
           (manifold.deferred IDeferred Deferrable)
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

(defn spyctx [label & [korks]]
  (fn [ctx]
    (debugf "SPYCTX %s: Context is %s" label
            (if korks (get-in ctx (if (sequential? korks) korks [korks])) ctx))
    ctx))

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

(defn exists-flow [method state req status headers body post allow-origin]
  (fn [ctx]
    (case method
      (:get :head)
      (d/chain
       ctx

       ;; OK, let's pick the resource's state
       (fn [ctx]
         (d/chain
          state                         ; could be nil
          #(p/state % ctx)
          (fn [state] (if state (assoc-in ctx [:resource :state] state) ctx))))

       ;; Conditional request
       (fn [ctx]
         (let [state (get-in ctx [:resource :state])]
           (if-let [last-modified (yst/last-modified state)]

             (if-let [if-modified-since (some-> req
                                                (get-in [:headers "if-modified-since"])
                                                parse-date)]
               (let [last-modified (if (d/deferrable? last-modified) @last-modified last-modified)]

                 (if (<=
                      (.getTime last-modified)
                      (.getTime if-modified-since))

                   ;; exit with 304
                   (d/error-deferred
                    (ex-info "" (merge {:status 304
                                        ::http-response true}
                                       ctx)))

                   (assoc-in ctx [:response :headers "last-modified"] (format-date last-modified))))

               (do
                 (infof "No if-modified-since header: %s" (:headers req))
                 (or
                  (some->> (if (d/deferrable? last-modified) @last-modified last-modified)
                           format-date
                           (assoc-in ctx [:response :headers "last-modified"]))
                  ctx)))

             ctx)))

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

           (case method
             :head (if content-type
                     ;; We don't need to add Content-Length,
                     ;; Content-Range, Trailer or Tranfer-Encoding, as
                     ;; per rfc7231.html#section-3.3
                     (update-in ctx [:response :headers] assoc "content-type" content-type)
                     ctx)

             :get
             (d/chain

              ;; Determine body
              (cond
                body (p/body body ctx)
                state state       ; the state here can still be deferred
                )

              ;; serialize to representation (an existing string will be left intact)
              (fn [state]
                (rep/content state content-type))

              ;; on nil, compose default result (if in dev)
              #_(fn [x] (if x x (rep/content nil content-type)))

              (fn [body]
                (if (not= method :head)
                  (assoc-in ctx [:response :body] body)
                  ctx))

              (fn [ctx]
                (if content-type
                  (update-in ctx [:response :headers] assoc "content-type" content-type)
                  ctx
                  ))

              (fn [ctx]
                (if content-length
                  (update-in ctx [:response :headers] assoc "content-length" content-length)
                  ctx)))))))

      :put
      (d/chain
         ctx
         (link ctx
           (when-let [etag (get-in req [:headers "if-match"])]
             (when (not= etag (get-in ctx [:resource :etag]))
               (throw
                (ex-info "Precondition failed"
                         {:status 412
                          ::http-response true})))))

         ;; Do the put!
         (fn [ctx]
           (let [exists? (yst/exists? state)]
             (if-let [put (some-> ctx :resource-map :put)]
               ;; Custom put function
               ;; TODO Rename put to put!
               (p/put put ctx)
               ;; Default behaviour
               (if state
                 (yst/write! state ctx)
                 (throw (ex-info "No implementation of put" {}))))
             (assoc-in ctx [:response :status]
                       (if exists? 204 201)))))

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

         ;; TODO: Should separate p/post from p/interpret-post-result
         ;; because p/post could return a deferred, so we need to chain
         ;; them together rather than complicate logic in resource.clj
         (p/interpret-post-result (p/post post ctx) ctx))

       (fn [ctx]
         (-> ctx
             (assoc-in [:response :status] 200))))

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

(defn to-encoding [s encoding]
  (-> s
      (.getBytes)
      (java.io.ByteArrayInputStream.)
      (slurp :encoding encoding)))

(defn to-title-case [s]
  (when s
    (str/replace s #"(\w+)" (comp str/capitalize second))))

(defn print-request
  "Print the request. Used for TRACE."
  [req]
  (letfn [(println [& x]
            (apply print x)
            (print "\r\n"))]
    (println (format "%s %s %s"
                     (str/upper-case (name (:request-method req)))
                     (:uri req)
                     "HTTP/1.1"))
    (doseq [[h v] (:headers req)] (println (format "%s: %s" (to-title-case h) v)))
    (println)
    (when-let [body (:body req)]
      (print (slurp body)))))

;; Allowed methods

;; RFC 7231 - 8.1.3.  Registrations

;; | Method  | Safe | Idempotent | Reference     |
;; +---------+------+------------+---------------+
;; | CONNECT | no   | no         | Section 4.3.6 |
;; | DELETE  | no   | yes        | Section 4.3.5 |
;; | GET     | yes  | yes        | Section 4.3.1 |
;; | HEAD    | yes  | yes        | Section 4.3.2 |
;; | OPTIONS | yes  | yes        | Section 4.3.7 |
;; | POST    | no   | no         | Section 4.3.3 |
;; | PUT     | no   | yes        | Section 4.3.4 |
;; | TRACE   | yes  | yes        | Section 4.3.8 |


(def known-methods #{:connect :delete :get :head :options :post :put :trace})

(defn safe? [method]
  (contains? #{:get :head :options :trace} method))

(defn idempotent? [method]
  (contains? #{:delete :get :head :options :put :trace} method))

(defn allowed-methods [ctx]
  (let [rmap (:resource-map ctx)]
    (if-let [methods (:methods rmap)]
      (set (p/allowed-methods methods))
      (set
       (remove nil?
               (conj
                (filter safe? known-methods)
                (when (:put rmap) :put)
                (when (:post rmap) :post)
                (when (:delete rmap) :delete)))))))

(defn make-handler
  [{:keys
    [service-available?                 ; async-supported
     known-method?                      ; async-supported
     request-uri-too-long?

     methods

     status                             ; async-supported
     headers                            ; async-supported

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
     trace                              ; async-supported

     produces
     parameters

     ;; CORS
     allow-origin

     ] ;; :or {resource {}}

    :as resource-map

    :or {authorization (NoAuthorizationSpecified.)}
    }]

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
               req
               ;; The next form is commented because we want to provide
               ;; access from individual State protocol implementations
               ;; to raw ByteBufers. You can only read the body stream
               ;; once, when it is required. Subsequent calls to :body
               ;; will yield nil. There's no way round this short to
               ;; temporarily storing the whole (potentially huge) body
               ;; somewhere.
               #_(assoc req
                        ;; We assoc in a 'delayed' body to the context's
                        ;; version of the request. The first caller to
                        ;; deref causes the slurp (which to do ahead of
                        ;; time might be unnecessarily wasteful).
                        :body (delay
                               (if-let [body (:body req)]
                                 ;; TODO Don't slurp, read into a byte array
                                 ;; Does aleph provide access to the underlying javax.nio.ByteBuffer(s)?
                                 ;; (yes - provided option raw-stream? is true)
                                 (slurp body :encoding (or (character-encoding req) "utf8"))
                                 nil)))
               :resource-map resource-map})

             (d/chain
              ;; TODO Inline these macros, they don't buy much for their complexity

              (link ctx
                (let [res (p/service-available? service-available? ctx)]
                  (if-not (p/interpret-service-available res)
                    (d/error-deferred
                     (ex-info "" (merge {:status 503
                                         ::http-response true}
                                        (when-let [retry-after (p/retry-after res)] {:headers {"retry-after" retry-after}})))))))

              (link ctx
                (when-not
                    (if known-method?
                      (p/known-method? known-method? method)
                      (contains? known-methods method)
                      )
                  (d/error-deferred (ex-info "" {:status 501
                                                 ::http-response true}))))

              (link ctx
                (when (p/request-uri-too-long? request-uri-too-long? (:uri req))
                  (d/error-deferred (ex-info "" {:status 414
                                                 ::http-response true}))))

              ;; Method Not Allowed
              (link ctx
                (let [am (allowed-methods ctx)]
                  (infof "am is %s" (seq am) )
                  (when-not (contains? (allowed-methods ctx) method)
                    (d/error-deferred
                     (ex-info (format "Method not allowed: %s" method)
                              {:status 405
                               ::http-response true})))))

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
                            merged-params (merge (apply merge (vals (dissoc parameters :body)))
                                                 (when body {:body body}))]
                        (cond-> ctx
                          (not-empty merged-params) (assoc :parameters merged-params)))))))


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

              ;; TRACE
              (link ctx
                (if (= method :trace)
                  (d/error-deferred
                   (ex-info "TRACE"
                            (merge
                             {::http-response true}
                             (if trace
                               ;; custom user-supplied implementation
                               (-> (merge
                                    {:status 200}
                                    (merge-with
                                     merge
                                     {:headers {"content-type" "message/http;charset=utf8"}}
                                     ;; Custom code /can/ override (but really shouldn't)
                                     (p/trace trace req ctx)))
                                   (update-in [:body] to-encoding "utf8"))

                               ;; otherwise default implementation
                               (let [body (-> ctx
                                              :request
                                              ;; A client MUST NOT generate header fields in a TRACE request containing sensitive
                                              ;; data that might be disclosed by the response. For example, it would be foolish for
                                              ;; a user agent to send stored user credentials [RFC7235] or cookies [RFC6265] in a
                                              ;; TRACE request.
                                              (update-in [:headers] dissoc "authorization" "cookie")
                                              print-request
                                              with-out-str
                                              ;; only "7bit", "8bit", or "binary" are permitted (RFC 7230 8.3.1)
                                              (to-encoding "utf8")
                                              )]

                                 {:status 200
                                  :headers {"content-type" "message/http;charset=utf8"
                                            "content-length" (.length body)}
                                  :body body
                                  ::http-response true})))))))

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

                (case method
                  (:get :head)
                  (d/chain
                   ctx

                   ;; OK, let's pick the resource's state
                   (fn [ctx]
                     (d/chain
                      state             ; could be nil
                      #(p/state % ctx)
                      (fn [state] (if state (assoc-in ctx [:resource :state] state) ctx))))

                   ;; Conditional request
                   (link ctx
                     (let [state (get-in ctx [:resource :state])]
                       (if-let [last-modified (yst/last-modified state)]

                         (if-let [if-modified-since (some-> req
                                                            (get-in [:headers "if-modified-since"])
                                                            parse-date)]
                           (let [last-modified (if (d/deferrable? last-modified) @last-modified last-modified)]

                             (if (<=
                                  (.getTime last-modified)
                                  (.getTime if-modified-since))

                               ;; exit with 304
                               (d/error-deferred
                                (ex-info "" (merge {:status 304
                                                    ::http-response true}
                                                   ctx)))

                               (assoc-in ctx [:response :headers "last-modified"] (format-date last-modified))))

                           (do
                             (infof "No if-modified-since header: %s" (:headers req))
                             (or
                              (some->> (if (d/deferrable? last-modified) @last-modified last-modified)
                                       format-date
                                       (assoc-in ctx [:response :headers "last-modified"]))
                              ctx))))))

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

                       (case method
                         :head (if content-type
                                 ;; We don't need to add Content-Length,
                                 ;; Content-Range, Trailer or Tranfer-Encoding, as
                                 ;; per rfc7231.html#section-3.3
                                 (update-in ctx [:response :headers] assoc "content-type" content-type)
                                 ctx)

                         :get
                         (d/chain

                          ;; Determine body
                          (cond
                            body (p/body body ctx)
                            state state ; the state here can still be deferred
                            )

                          ;; serialize to representation (an existing string will be left intact)
                          (fn [state]
                            (rep/content state content-type))

                          ;; on nil, compose default result (if in dev)
                          #_(fn [x] (if x x (rep/content nil content-type)))

                          (fn [body]
                            (if (not= method :head)
                              (assoc-in ctx [:response :body] body)
                              ctx))

                          (fn [ctx]
                            (if content-type
                              (update-in ctx [:response :headers] assoc "content-type" content-type)
                              ctx
                              ))

                          (fn [ctx]
                            (if content-length
                              (update-in ctx [:response :headers] assoc "content-length" content-length)
                              ctx)))))))

                  :put
                  (d/chain
                   ctx
                   (link ctx
                     (when-let [etag (get-in req [:headers "if-match"])]
                       (when (not= etag (get-in ctx [:resource :etag]))
                         (throw
                          (ex-info "Precondition failed"
                                   {:status 412
                                    ::http-response true})))))

                   ;; Do the put!
                   (fn [ctx]
                     (let [exists? (yst/exists? state)]
                       (if-let [put (some-> ctx :resource-map :put)]
                         ;; Custom put function
                         ;; TODO Rename put to put!
                         (p/put put ctx)
                         ;; Default behaviour
                         (if state
                           (yst/write! state ctx)
                           (throw (ex-info "No implementation of put" {}))))
                       (assoc-in ctx [:response :status]
                                 (if exists? 204 201)))))

                  :post
                  (d/chain
                   ctx

                   (link ctx
                     (when-let [etag (get-in req [:headers "if-match"])]
                       (when (not= etag (get-in ctx [:resource :etag]))
                         (throw
                          (ex-info "Precondition failed"
                                   {:status 412
                                    ::http-response true})))))

                   (fn [ctx]
                     ;; TODO: what if error?

                     ;; TODO: Should separate p/post from p/interpret-post-result
                     ;; because p/post could return a deferred, so we need to chain
                     ;; them together rather than complicate logic in resource.clj
                     (p/interpret-post-result (p/post post ctx) ctx))

                   (fn [ctx]
                     (-> ctx
                         (assoc-in [:response :status] 200))))

                  :options
                  (d/chain
                   ctx
                   (link ctx
                     (if-let [origin (p/allow-origin allow-origin ctx)]
                       (update-in ctx [:response :headers]
                                  merge {"access-control-allow-origin"
                                         origin
                                         "access-control-allow-methods"
                                         (apply str
                                                (interpose ", " ["GET" "POST" "PUT" "DELETE"]))}))))

                  (throw (ex-info "Unknown method"
                                  {:status 501
                                   :http-response true})))

                #_(cond
                    ;; 'Exists' flow
                    ;; TODO: Not sure that exists-flow is what we're doing here - exists-flow has all kinds of things complected in it. Break up into individual cases, perhaps based on method. We are only using this to avoid the 404 of the 'not exists' flow. Perhaps check for existence and throw the 404 now if necessary.
                    (or state body (#{:post :put} method))
                    (d/chain
                     ctx
                     ;; Not sure we should use exists-flow for CORS pre-flight requests, should handle further above
                     (exists-flow method state req status headers body post allow-origin)
                     (cors allow-origin)

                     (fn [ctx]
                       (merge
                        {:status (or (get-in ctx [:response :status])
                                     (p/status status ctx)
                                     200)
                         :headers (merge
                                   (get-in ctx [:response :headers])
                                   (p/headers headers ctx))
                         ;; TODO :status and :headers should be implemented like this in all cases
                         :body (get-in ctx [:response :body])}))
                     )

                    ;; 'Not exists' flow
                    :otherwise
                    (d/chain ctx (constantly {:status 404}))))

              (fn [ctx]
                (merge
                 {:status (or (get-in ctx [:response :status])
                              (p/status status ctx)
                              200)
                  :headers (merge
                            (get-in ctx [:response :headers])
                            (p/headers headers ctx))
                  ;; TODO :status and :headers should be implemented like this in all cases
                  :body (get-in ctx [:response :body])}))

              (fn [ctx]
               (if-let [origin (p/allow-origin allow-origin ctx)]
                 (update-in ctx [:response :headers]
                            merge {"access-control-allow-origin"
                                   origin
                                   "access-control-expose-headers"
                                   (apply str
                                          (interpose ", " ["Server" "Date" "Content-Length" "Access-Control-Allow-Origin"]))})
                 ctx))

              ;; End of chain
              )

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
                             [:pre (with-out-str (apply str (interpose "\n" (seq (.getStackTrace %)))))]])}))))))))

(defn yada [& args]
  (if (keyword? (first args))
    (make-handler (into {} (map vec (partition 2 args))))
    (let [handler (make-handler (first args))]
      (if (= (count args) 2)
        ;; 2-arity function means the 2nd argument is a request. This
        ;; means the yada handler is created just-in-time. This is
        ;; useful when certain values are only known at runtime, for
        ;; example, when using destructured args from Compojure routes
        ;; in resource-maps.
        (handler (second args))

        ;; Otherwise the handler is returned. TODO: Replace this
        ;; implementation with one using clojure.core/partial, because
        ;; that is essentially the model.
        handler))))
