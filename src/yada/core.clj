;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:refer-clojure :exclude [methods])
  (:require
   [byte-streams :refer (convert)]
   [bidi.bidi :refer (Matched succeed)]
   [bidi.ring :refer (Ring)]
   [cheshire.core :as json]
   [clojure.core.async :as a]
   [clojure.java.io :as io]
   [clojure.pprint :refer (pprint)]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.walk :refer (keywordize-keys)]
   [hiccup.core :refer (html h)]
   [manifold.deferred :as d]
   [manifold.stream :refer (->source transform)]
   [ring.middleware.basic-authentication :as ba]
   [ring.middleware.params :refer (assoc-query-params)]
   [ring.swagger.coerce :as rc]
   [ring.swagger.schema :as rs]
   [ring.util.codec :refer (form-decode)]
   [ring.util.request :refer (character-encoding urlencoded-form? content-type)]
   [ring.util.time :refer (parse-date format-date)]
   [schema.coerce :refer (coercer string-coercion-matcher) :as sc]
   [schema.core :as s]
   [schema.utils :refer (error? error-val)]
   [yada.coerce :refer (coercion-matcher)]
   [yada.charset :as charset]
   [yada.response :refer (->Response)]
   [yada.representation :as rep]
   [yada.methods :as methods]
   [yada.negotiation :as negotiation]
   [yada.service :as service]
   [yada.resource :as res]
   [yada.mime :as mime]
   [yada.util :refer (link)]
   manifold.deferred
   schema.utils)
  (:import (clojure.lang IPending)
           (java.util Date)
           (java.util.concurrent Future)
           (manifold.deferred IDeferred Deferrable)
           (schema.utils ValidationError ErrorContainer)))

(defn make-context []
  {:response (->Response)})

(defrecord HttpResource [resource id handler]
  clojure.lang.IFn
  (invoke [_ req]
    (let [ctx (make-context)]
      (handler req ctx)))
  Matched
  (resolve-handler [this m]
    (succeed this m))
  (unresolve-handler [this m]
    (when (#{id this} (:handler m)) "")))

;; "It is better to have 100 functions operate on one data structure
;; than 10 functions on 10 data structures." — Alan Perlis

(defn spyctx [label & [korks]]
  (fn [ctx]
    (infof "SPYCTX %s: Context is %s"
            label
            (pr-str (if korks (get-in ctx (if (sequential? korks) korks [korks])) ctx)))
    ctx))

(defrecord NoAuthorizationSpecified []
  service/Service
  (authorize [b ctx] true)
  (authorization [_] nil))

(def realms-xf
  (comp
   (filter (comp (partial = :basic) :type))
   (map :realm)))

(defn as-sequential [s]
  (if (sequential? s) s [s]))

;; TODO: Read and understand the date algo presented in RFC7232 2.2.1

(defn round-seconds-up
  "Round up to the nearest second. The rationale here is that
  last-modified times from resources have a resolution of a millisecond,
  but HTTP dates have a resolution of a second. This makes testing 304
  harder. By ceiling every date to the nearest (future) second, we
  side-step this problem, constructing a 'weak' validator. See
  rfc7232.html 2.1. If an update happens a split-second after the
  previous update, it's possible that a client might miss an
  update. However, the point is that user agents using dates don't
  generally care about having the very latest if they're using
  If-Modified-Since, otherwise they'd omit the header completely. In the
  spec. this is allowable semantics under the rules of weak validators.

  TODO: This violates this part of the spec. Need an alternative implementation
  \"An origin server with a clock MUST NOT send a Last-Modified date that
   is later than the server's time of message origination (Date).\" — RFC7232 2.2.1 "
  [d]
  (when d
    (let [n (.getTime d)
          m (mod n 1000)]
      (if (pos? m)
        (Date. (+ (- n m) 1000))
        d))))

(defn read-body [req]
  (when-let [body (:body req)]
    (convert body String {:encoding (or (character-encoding req) "UTF-8")})))

(defn resource
  "Create a yada resource (Ring handler)"
  ([resource]                         ; Single-arity form with default options
   (yada.core/resource resource {}))

  ([resource
    {:keys
     ;; TODO: Replace these with inline extracts from options to reduce
     ;; scope complexity
     [service-available?                ; async-supported
      request-uri-too-long?

      status                            ; async-supported
      headers                           ; async-supported

      ;; Security
      authorization                     ; async-supported
      security

      id ;; bidi compatible identifier

      ;; CORS
      allow-origin
      ] :as options
     :or {authorization (NoAuthorizationSpecified.)
          id (java.util.UUID/randomUUID)}}]

   (let [base resource ; keep a copy, we're about to eclipse
         resource (if (satisfies? res/ResourceCoercion resource)
                    (res/make-resource resource)
                    resource)

         known-methods (methods/methods)

         allowed-methods
         (set
          (or (:methods options) ; you must include :head in options if you want it
              ;; We always support :head for resources
              (conj (res/methods resource) :head)))

         parameters (or (:parameters options)
                        (when (satisfies? res/ResourceParameters resource)
                          (res/parameters resource)))

         representations
         (negotiation/parse-representations
          (or
           (:representations options)
           (when-let [rep (:representation options)] [rep])
           (let [m (select-keys options [:content-type :charset :encoding :language])]
             (when (not-empty m) [m]))
           (when (satisfies? res/ResourceRepresentations resource)
             (res/representations resource))))

         etag? (satisfies? res/ResourceEntityTag resource)

         security (as-sequential security)]

     (map->HttpResource
      {:id id
       :resource resource
       :base base
       :options options
       :methods allowed-methods
       :parameters parameters
       :representations representations
       :security security
       :authorization authorization
       :handler
       (fn [req ctx]

         (let [method (:request-method req)
               ;; TODO: Document this debug feature
               debug (boolean (get-in req [:headers "x-yada-debug"]))]

           (-> ctx

               (merge
                {:method method
                 :method-instance (get known-methods method)
                 :allowed-methods allowed-methods
                 :request req
                 :options options})

               (d/chain

                ;; Available?
                (link ctx
                  (let [res (service/service-available? service-available? ctx)]
                    (if-not (service/interpret-service-available res)
                      (d/error-deferred
                       (ex-info "" (merge {:status 503
                                           ::http-response true}
                                          (when-let [retry-after (service/retry-after res)] {:headers {"retry-after" retry-after}})))))))

                ;; Known method?
                (link ctx
                  (when-not (:method-instance ctx)
                    (d/error-deferred (ex-info "" {:status 501 ::method method ::http-response true}))))

                ;; URI too long?
                (link ctx
                  (when (service/request-uri-too-long? request-uri-too-long? (:uri req))
                    (d/error-deferred (ex-info "" {:status 414
                                                   ::http-response true}))))

                ;; TRACE
                (link ctx
                  (when (#{:trace} method)
                    (if (false? (:trace options))
                      (d/error-deferred (ex-info "Method Not Allowed"
                                                 {:status 405
                                                  ::http-response true}))
                      (methods/request (:method-instance ctx) ctx))))

                ;; Is method allowed on this resource?
                (link ctx
                  (when-not (contains? allowed-methods method)
                    (d/error-deferred
                     (ex-info "Method Not Allowed"
                              {:status 405
                               :headers {"allow" (str/join ", " (map (comp (memfn toUpperCase) name) allowed-methods))}
                               ::http-response true}))))

                ;; Malformed? (parameters)
                (fn [ctx]
                  (let [keywordize (fn [m] (into {} (for [[k v] m] [(keyword k) v])))

                        parameters
                        (when parameters
                          {:path
                           (when-let [schema (get-in parameters [method :path])]
                             (rs/coerce schema (:route-params req) :query))

                           :query
                           (when-let [schema (get-in parameters [method :query])]
                             ;; We'll call assoc-query-params with the negotiated charset, falling back to UTF-8.
                             ;; Also, read this:
                             ;; http://www.w3.org/TR/html5/forms.html#application/x-www-form-urlencoded-encoding-algorithm
                             (rs/coerce schema (-> req (assoc-query-params (or (:charset ctx) "UTF-8")) :query-params keywordize) :query))

                           :body
                           (when-let [schema (get-in parameters [method :body])]
                             (let [body (read-body (-> ctx :request))]
                               (rep/from-representation body (content-type req) schema)))

                           :form
                           ;; TODO: Can we use rep:from-representation
                           ;; instead? It's virtually the same logic for
                           ;; url encoded forms
                           (when-let [schema (get-in parameters [method :form])]
                             (when (urlencoded-form? req)
                               (let [fp (keywordize-keys
                                         (form-decode (read-body (-> ctx :request))
                                                      (character-encoding req)))]
                                 (rs/coerce schema fp :json))))

                           :header
                           (when-let [schema (get-in parameters [method :header])]
                             (let [params (select-keys (-> req :headers keywordize-keys) (keys schema))]
                               (rs/coerce schema params :query)))})]

                    (let [errors (filter (comp error? second) parameters)]
                      (if (not-empty errors)
                        (d/error-deferred (ex-info "" {:status 400
                                                       :body errors
                                                       ::http-response true}))

                        (if parameters
                          (let [body (:body parameters)
                                merged-params (merge (apply merge (vals (dissoc parameters :body)))
                                                     (when body {:body body}))]
                            (cond-> ctx
                              (not-empty merged-params) (assoc :parameters merged-params)))
                          ctx)))))

                ;; Authentication
                (fn [ctx]
                  (cond-> ctx
                    (not-empty (filter (comp (partial = :basic) :type) security))
                    (assoc :authentication (:basic-authentication (ba/basic-authentication-request req (fn [user password] {:user user :password password}))))))

                ;; Authorization
                (fn [ctx]
                  (if-let [res (service/authorize authorization ctx)]
                    (if (= res :not-authorized)
                      (d/error-deferred
                       (ex-info ""
                                (merge
                                 {:status 401 ::http-response true}
                                 (when-let [basic-realm (first (sequence realms-xf security))]
                                   {:headers {"www-authenticate" (format "Basic realm=\"%s\"" basic-realm)}}))
                                ))
                      (if-let [auth (service/authorization res)]
                        (assoc ctx :authorization auth)
                        ctx))
                    (d/error-deferred (ex-info "" {:status 403
                                                   ::http-response true}))))

                ;; Now we do a fetch, so the resource has a chance to
                ;; load any metadata it may need to answer the questions
                ;; to follow. It can also load state in this step, if it
                ;; wants, but can defer this to the get-state call if it
                ;; wants. This would usually depend on the size of the
                ;; state. For example, if it's a stream, it would be
                ;; returned on get-state.

                ;; Do the fetch here - this is intended to allow
                ;; resources to prepare to answer questions about
                ;; themselves. This has to happen _after_ the request
                ;; parameters have been established because the fetch
                ;; might depend on them in some way. The fetch may
                ;; involve a database trip, to load context based on the
                ;; request parameters.

                ;; The fetch can return a deferred result.
                (fn [ctx]
                  (d/chain
                   (res/fetch resource ctx)
                   (fn [res]
                     (assoc ctx :resource res))))

                ;; TODO: Unknown or unsupported Content-* header

                ;; TODO: Request entity too large - shouldn't we do this later,
                ;; when we determine we actually need to read the request body?

                ;; Content negotiation
                ;; TODO: Unknown Content-Type? (incorporate this into conneg)

                (link ctx

                  ;; We do not do negotiation for representations if the
                  ;; request has some path-info - that makes it
                  ;; context-sensitive and negotiation must be done
                  ;; dynamically in the resource implementation.

                  ;; (TODO: We should apply the same rationale to methods
                  ;; and parameters)

                  ;; TODO: It is better that we simply filter out
                  ;; representations that are guarded by a path-info
                  ;; pattern in the representations.
                  (when (nil? (:path-info req))

                    ;; TODO Use yada.resource/Negotiable, cache the
                    ;; satisfies? on the resource first, it's expensive to
                    ;; do on every request

                    (let [negotiated
                          (when-let [res (first
                                          (negotiation/negotiate
                                           (negotiation/extract-request-info req)
                                           representations))]
                            (negotiation/interpret-negotiation res))]

                      (if (:status negotiated)
                        (d/error-deferred (ex-info "" (merge negotiated {::http-response true})))

                        (let [vary (negotiation/vary method representations)]
                          (cond-> ctx
                            negotiated (assoc-in [:response :representation] negotiated)
                            vary (assoc-in [:response :vary] vary)))))))

                ;; Resource exists?
                (link ctx
                  (d/chain
                   (res/exists? resource ctx)
                   (fn [exists?]
                     (assoc ctx :exists? exists?))))

                ;; Conditional requests - last modified time
                (fn [ctx]
                  (d/chain
                   (or
                    (res/last-modified (:last-modified options) ctx)
                    (res/last-modified (:resource ctx) ctx))

                   (fn [last-modified]
                     (if-let [last-modified (round-seconds-up last-modified)]

                       (if-let [if-modified-since (some-> req
                                                          (get-in [:headers "if-modified-since"])
                                                          parse-date)]

                         (if (<=
                              (.getTime last-modified)
                              (.getTime if-modified-since))

                           ;; exit with 304
                           (d/error-deferred
                            (ex-info "" (merge {:status 304
                                                ::http-response true}
                                               ctx)))

                           (assoc-in ctx [:response :headers "last-modified"] (format-date last-modified)))

                         (or
                          (some->> last-modified
                                   format-date
                                   (assoc-in ctx [:response :headers "last-modified"]))
                          ctx))
                       ctx))))

                ;; ETags
                (link ctx

                  (when etag?
                    (let [etag (res/etag (:resource ctx))]
                      (assoc-in ctx [:response :headers "etag"] etag)))

                  #_(when-let [etag (get-in ctx [:request :headers "if-match"])]
                    (when (not= etag (get-in ctx [:resource :etag]))
                      (throw
                       (ex-info "Precondition failed"
                                {:status 412
                                 ::http-response true})))))

                ;; Methods
                (fn [ctx]
                  (methods/request (:method-instance ctx) ctx))

                ;; CORS
                ;; TODO: Reinstate
                #_(fn [ctx]
                    (if-let [origin (service/allow-origin allow-origin ctx)]
                      (update-in ctx [:response :headers]
                                 merge {"access-control-allow-origin"
                                        origin
                                        "access-control-expose-headers"
                                        (apply str
                                               (interpose ", " ["Server" "Date" "Content-Length" "Access-Control-Allow-Origin"]))})
                      ctx))

                ;; Response
                (fn [ctx]
                  (let [response
                        {:status (or (get-in ctx [:response :status])
                                     (service/status status ctx)
                                     200)
                         :headers (merge
                                   (get-in ctx [:response :headers])
                                   ;; TODO: The context and its response
                                   ;; map must be documented so users are
                                   ;; clear what they can change and the
                                   ;; effect of this change.
                                   (when-let [ct (get-in ctx [:response :representation :content-type])]
                                     {"content-type" (mime/media-type->string ct)})
                                   (when-let [lang (get-in ctx [:response :representation :language])]
                                     {"content-language" lang})
                                   (when-let [cl (get-in ctx [:response :content-length])]
                                     {"content-length" cl})
                                   (when-let [vary (get-in ctx [:response :vary])]
                                     {"vary" (negotiation/to-vary-header vary)})


                                   #_(when true
                                       {"access-control-allow-origin" "*"})

                                   (service/headers headers ctx))

                         ;; TODO :status and :headers should be implemented like this in all cases
                         :body (get-in ctx [:response :body])}]
                    (debugf "Returning response: %s" (dissoc response :body))
                    response)))

               ;; Handle exits
               (d/catch clojure.lang.ExceptionInfo
                   #(let [data (ex-data %)]
                      (if (::http-response data)
                        (if-let [debug-data (when debug (::debug data))]
                          (assoc data :body (prn-str debug-data))
                          data)

                        (throw (ex-info "Internal Server Error (ex-info)" data %))
                        #_{:status 500
                           :body (format "Internal Server Error: %s" (prn-str data))})))

               (d/catch #(identity
                          (throw (ex-info "Internal Server Error" {:request req} %))
                          #_{:status 500 :body
                             (html
                              [:body
                               [:h1 "Internal Server Error"]
                               [:p (str %)]
                               [:pre (with-out-str (apply str (interpose "\n" (seq (.getStackTrace %)))))]])})))))}))))
