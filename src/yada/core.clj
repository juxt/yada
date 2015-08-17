;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [byte-streams :as bs]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.walk :refer (keywordize-keys)]
   [manifold.deferred :as d]
   ring.middleware.basic-authentication
   [ring.middleware.params :refer (assoc-query-params)]
   [ring.swagger.schema :as rs]
   ring.util.codec
   [ring.util.request :as req]
   ring.util.time
   schema.utils
   [yada.body :as body]
   [yada.charset :as charset]
   [yada.methods :as methods]
   [yada.representation :as rep]
   [yada.protocols :as p]
   [yada.response :refer (->Response)]
   [yada.service :as service]
   [yada.media-type :as mt]
   [yada.util :refer (parse-csv)])
  (:import (java.util Date)))

(defn make-context []
  {:response (->Response)})

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
    (bs/convert body String {:encoding (or (req/character-encoding req) "UTF-8")})))

(def realms-xf
  (comp
   (filter (comp (partial = :basic) :type))
   (map :realm)))

(defn available?
  "Is the service available?"
  [ctx]
  (let [res (service/service-available? (-> ctx :http-resource :service-available?) ctx)]
    (if-not (service/interpret-service-available res)
      (d/error-deferred
       (ex-info "" (merge {:status 503
                           ::http-response true}
                          (when-let [retry-after (service/retry-after res)] {:headers {"retry-after" retry-after}}))))
      ctx)))

(defn known-method?
  [ctx]
  (if-not (:method-instance ctx)
    (d/error-deferred (ex-info "" {:status 501 ::method (:method ctx) ::http-response true}))
    ctx))

(defn uri-too-long?
  [ctx]
  (if (service/request-uri-too-long? (-> ctx :options :request-uri-too-long?) (-> ctx :request :uri))
    (d/error-deferred (ex-info "" {:status 414 ::http-response true}))
    ctx))

(defn TRACE
  [ctx]
  (if (#{:trace} (:method ctx))
    (if (false? (-> ctx :options :trace))
      (d/error-deferred
       (ex-info "Method Not Allowed"
                {:status 405 ::http-response true}))
      (methods/request (:method-instance ctx) ctx))
    ctx))

(defn method-allowed?
  "Is method allowed on this resource?"
  [ctx]
  (if-not (contains? (-> ctx :allowed-methods) (:method ctx))
    (d/error-deferred
     (ex-info "Method Not Allowed"
              {:status 405
               :headers {"allow" (str/join ", " (map (comp (memfn toUpperCase) name) (-> ctx :http-resource :allowed-methods)))}
               ::http-response true}))
    ctx))

(defn malformed?
  "Malformed? (parameters)"
  [ctx]
  (let [method (:method ctx)
        request (:request ctx)
        keywordize (fn [m] (into {} (for [[k v] m] [(keyword k) v])))

        parameters (-> ctx :http-resource :parameters)

        parameters
        (when parameters
          {:path
           (when-let [schema (get-in parameters [method :path])]
             (rs/coerce schema (:route-params request) :query))

           :query
           (when-let [schema (get-in parameters [method :query])]
             ;; We'll call assoc-query-params with the negotiated charset, falling back to UTF-8.
             ;; Also, read this:
             ;; http://www.w3.org/TR/html5/forms.html#application/x-www-form-urlencoded-encoding-algorithm
             (rs/coerce schema (-> request (assoc-query-params (or (:charset ctx) "UTF-8")) :query-params keywordize) :query))

           :body
           (when-let [schema (get-in parameters [method :body])]
             (let [body (read-body (-> ctx :request))]
               (body/coerce-request-body body (req/content-type request) schema)))

           :form
           ;; TODO: Can we use rep:from-representation
           ;; instead? It's virtually the same logic for
           ;; url encoded forms
           (when-let [schema (get-in parameters [method :form])]
             (when (req/urlencoded-form? request)
               (let [fp (keywordize-keys
                         (ring.util.codec/form-decode (read-body (-> ctx :request))
                                                      (req/character-encoding request)))]
                 (rs/coerce schema fp :json))))

           :header
           (when-let [schema (get-in parameters [method :header])]
             (let [params (select-keys (-> request :headers keywordize-keys) (keys schema))]
               (rs/coerce schema params :query)))})]

    (let [errors (filter (comp schema.utils/error? second) parameters)]
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

(defn authentication
  "Authentication"
  [ctx]
  (cond-> ctx
    (not-empty (filter (comp (partial = :basic) :type) (-> ctx :http-resource :security)))
    (assoc :authentication
           (:basic-authentication (ring.middleware.basic-authentication/basic-authentication-request
                                   (:request ctx)
                                   (fn [user password] {:user user :password password}))))))

(defn authorization
  "Authorization"
  [ctx]
  (if-let [res (service/authorize (-> ctx :http-resource :authorization) ctx)]
    (if (= res :not-authorized)
      (d/error-deferred
       (ex-info ""
                (merge
                 {:status 401 ::http-response true}
                 (when-let [basic-realm (first (sequence realms-xf (-> ctx :http-resource :security)))]
                   {:headers {"www-authenticate" (format "Basic realm=\"%s\"" basic-realm)}}))))

      (if-let [auth (service/authorization res)]
        (assoc ctx :authorization auth)
        ctx))

    (d/error-deferred (ex-info "" {:status 403 ::http-response true}))))

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
(defn fetch
  [ctx]
  (d/chain
   (p/fetch (:resource ctx) ctx)
   (fn [res]
     (assoc ctx :resource res))))

;; Content negotiation
;; TODO: Unknown Content-Type? (incorporate this into conneg)
(defn select-representation
  [ctx]
  (let [representation
        (rep/select-representation (:request ctx) (-> ctx :http-resource :representations))]

    (if (nil? representation)
      (d/error-deferred
       (ex-info "" {:status 406
                    ::http-response true}))

      (cond-> ctx
        representation (assoc-in [:response :representation] representation)
        (-> ctx :http-resource :vary) (assoc-in [:response :vary] (-> ctx :http-resource :vary))))))

(defn exists?
  "A current representation for the resource exists?"
  [ctx]
  (if (-> ctx :http-resource :existence?)
    (d/chain
     (p/exists? (:resource ctx) ctx)
     (fn [exists?]
       (assoc ctx :exists? exists?)))
    ;; Default
    (assoc ctx :exists? true)))

;; Conditional requests - last modified time
(defn check-modification-time [ctx]
  (d/chain
   (or
    (p/last-modified (-> ctx :options :last-modified) ctx)
    (p/last-modified (:resource ctx) ctx))

   (fn [last-modified]
     (if-let [last-modified (round-seconds-up last-modified)]

       (if-let [if-modified-since
                (some-> (:request ctx)
                        (get-in [:headers "if-modified-since"])
                        ring.util.time/parse-date)]
         (if (<=
              (.getTime last-modified)
              (.getTime if-modified-since))

           ;; exit with 304
           (d/error-deferred
            (ex-info "" (merge {:status 304 ::http-response true} ctx)))

           (assoc-in ctx [:response :last-modified] (ring.util.time/format-date last-modified)))

         (or
          (some->> last-modified
                   ring.util.time/format-date
                   (assoc-in ctx [:response :last-modified]))
          ctx))
       ctx))))

;; Check ETag - we already have the representation details,
;; which are necessary for a strong validator. See
;; section 2.3.3 of RFC 7232.

;; If-Match check
(defn if-match
  [ctx]

  (if-let [matches (some->> (get-in (:request ctx) [:headers "if-match"]) parse-csv set)]

    ;; We have an If-Match to process
    (cond
      (and (contains? matches "*") (-> ctx :http-resource :representations count pos?))
      ;; No need to compute etag, exit
      ctx

      ;; Otherwise we need to compute the etag for each current
      ;; representation of the resource

      ;; Create a map of representation -> etag
      (-> ctx :http-resource :version?)
      (let [version (p/version (:resource ctx) ctx)
            etags (into {} (map (juxt identity (partial p/to-etag version))
                                (-> ctx :http-resource :representations)))]

        (if (empty? (set/intersection matches (set (vals etags))))
          (d/error-deferred
           (ex-info "Precondition failed"
                    {:status 412
                     ::http-response true}))

          ;; Otherwise, let's use the etag we've just
          ;; computed. Note, this might yet be
          ;; overridden by the (unsafe) method returning
          ;; a modified response. But if the method
          ;; chooses not to reset the etag (perhaps the
          ;; resource state didn't change), then this
          ;; etag will do for the response.
          (assoc-in ctx [:response :etag]
                    (get etags (:representation ctx))))))
    ctx))

(defn invoke-method
  "Methods"
  [ctx]
  (methods/request (:method-instance ctx) ctx))

;; Compute ETag, if not already done so

;; Unsafe resources that support etags MUST return a
;; response which contains a version if they mutate the
;; resource's state. If they don't return a version, no
;; etag (or worse, a previously computed etag) will be
;; set in the response header.

;; Produce ETag: The "ETag" header field in a response provides
;; the current entity-tag for the selected
;; representation, as determined at the conclusion of
;; handling the request. RFC 7232 section 2.3

;; If we have just mutated the resource, we should
;; recompute the etag

(defn compute-etag
  [ctx]
  ;; only if resource supports etags
  (if (-> ctx :http-resource :version?)
    ;; only if the resource hasn't already set an etag
    (when-let [version (or (-> ctx :response :version)
                           (p/version (:resource ctx) ctx))]
      (let [etag (p/to-etag version (get-in ctx [:response :representation]))]
        (assoc-in ctx [:response :etag] etag)))
    ctx))

;; CORS
;; TODO: Reinstate
#_(defn cors [ctx]
  (if-let [origin (service/allow-origin allow-origin ctx)]
    (update-in ctx [:response :headers]
               merge {"access-control-allow-origin"
                      origin
                      "access-control-expose-headers"
                      (apply str
                             (interpose ", " ["Server" "Date" "Content-Length" "Access-Control-Allow-Origin"]))})
    ctx))

;; Response
(defn create-response
  [ctx]
  (let [response
        {:status (or (get-in ctx [:response :status])
                     (service/status (-> ctx :http-resource :options :status) ctx)
                     200)
         :headers (merge
                   (get-in ctx [:response :headers])
                   ;; TODO: The context and its response
                   ;; map must be documented so users are
                   ;; clear what they can change and the
                   ;; effect of this change.
                   (when (not= (:method ctx) :options)
                     (merge {}
                            (when-let [x (get-in ctx [:response :representation :media-type])]
                              (let [y (get-in ctx [:response :representation :charset])]
                                (if (and y (= (:type x) "text"))
                                  {"content-type" (mt/media-type->string (assoc-in x [:parameters "charset"] (charset/charset y)))}
                                  {"content-type" (mt/media-type->string x)})))
                            (when-let [x (get-in ctx [:response :representation :encoding])]
                              {"content-encoding" x})
                            (when-let [x (get-in ctx [:response :representation :language])]
                              {"content-language" x})
                            (when-let [x (get-in ctx [:response :last-modified])]
                              {"last-modified" x})
                            (when-let [x (get-in ctx [:response :vary])]
                              (when (not-empty x)
                                {"vary" (rep/to-vary-header x)}))
                            (when-let [x (get-in ctx [:response :etag])]
                              {"etag" x})))
                   (when-let [x (get-in ctx [:response :content-length])]
                     {"content-length" x})

                   #_(when true
                       {"access-control-allow-origin" "*"})

                   ;; TODO: Resources can add headers via their methods
                   (service/headers (-> ctx :http-resource :options :headers) ctx))

         ;; TODO :status and :headers should be implemented like this in all cases
         :body (get-in ctx [:response :body])}]
    (debugf "Returning response: %s" (dissoc response :body))
    response))

(defn wrap-trace [link]
  (fn [ctx]
    (let [t0 (System/nanoTime)]
      (d/chain
       (link ctx)
       (fn [newctx]
         (let [t1 (System/nanoTime)]
           (update-in newctx [:chain] conj {:link link
                                            :old (dissoc ctx :chain)
                                            :new (dissoc newctx :chain)
                                            :t0 t0 :t1 t1})))))))

(defn- handle-request
  "Handle Ring request"
  [http-resource request]
  (let [method (:request-method request)
        interceptors (:interceptors http-resource)
        options (:options http-resource)
        ctx (merge
             (make-context)
             {:method method
              :method-instance (get (:known-methods http-resource) method)
              :interceptors interceptors
              :http-resource http-resource
              :resource (:resource http-resource)
              :request request
              :allowed-methods (:allowed-methods http-resource)
              :options options})]

    (->
     (->> interceptors
          (map wrap-trace)
          (apply d/chain ctx))

     ;; Handle non-local exits
     (d/catch
         (fn [e]
           (let [data (when (instance? clojure.lang.ExceptionInfo e) (ex-data e))]
             (if (::http-response data)
               data
               (throw (ex-info "Internal Server Error (ex-info)" data e)))))))))

(defrecord HttpResource [resource id handler]
  clojure.lang.IFn
  (invoke [this req]
    (handle-request this req)))

(defrecord NoAuthorizationSpecified []
  service/Service
  (authorize [b ctx] true)
  (authorization [_] nil))

(defn as-sequential [s]
  (if (sequential? s) s [s]))

(def default-interceptor-chain
  [available?
   known-method?
   uri-too-long?
   TRACE
   method-allowed?
   malformed?
   authentication
   authorization
   fetch
   ;; TODO: Unknown or unsupported Content-* header
   ;; TODO: Request entity too large - shouldn't we do this later,
   ;; when we determine we actually need to read the request body?
   select-representation
   exists?
   check-modification-time
   if-match
   invoke-method
   compute-etag
   create-response])

(defn resource
  "Create a yada resource (Ring handler)"
  ([resource]                   ; Single-arity form with default options
   (yada.core/resource resource {}))

  ([resource options]
   (let [base resource

         resource (if (satisfies? p/ResourceCoercion resource)
                    (p/as-resource resource)
                    resource)

         known-methods (methods/known-methods)

         allowed-methods (conj
                          (set
                           (or (:allowed-methods options)
                               (p/allowed-methods resource)))
                          :head :options)

         parameters (or (:parameters options)
                        (when (satisfies? p/ResourceParameters resource)
                          (p/parameters resource)))

         representations (rep/representation-seq
                          (rep/coerce-representations
                           (or
                            (:representations options)
                            (when-let [rep (:representation options)] [rep])
                            (let [m (select-keys options [:media-type :charset :encoding :language])]
                              (when (not-empty m) [m]))
                            (when (satisfies? p/Representations resource)
                              (p/representations resource)))))

         vary (rep/vary representations)
         ]

     (map->HttpResource
      {:id (or (:id options) (java.util.UUID/randomUUID))
       :resource resource
       :base base
       :interceptors default-interceptor-chain
       :options options
       :allowed-methods allowed-methods
       :known-methods known-methods
       :parameters parameters
       :representations representations
       :vary vary
       :security (as-sequential (:security options))
       :service-available? (:service-available? options)
       :authorization (or (:authorization options) (NoAuthorizationSpecified.))
       :version? (satisfies? p/ResourceVersion resource)
       :existence? (satisfies? p/RepresentationExistence resource)}))))
