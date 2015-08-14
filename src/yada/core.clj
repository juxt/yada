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
   [yada.charset :as charset]
   [yada.methods :as methods]
   [yada.representation :as rep]
   [yada.resource :as res]
   [yada.response :refer (->Response)]
   [yada.service :as service]
   [yada.mime :as mime]
   [yada.util :refer (parse-csv)])
  (:import (java.util Date)))

(defn make-context []
  {:response (->Response)})

(defmacro link [ctx body]
  `(fn [~ctx] (or ~body ~ctx)))

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

(defn- handle-request [http-resource request]
  (let [ctx (make-context)]
    (let [method (:request-method request)
          options (:options http-resource)

          ;; TODO: Document this debug feature
          debug (boolean (get-in request [:headers "x-yada-debug"]))]

      (-> ctx

          ;; Populate context
          (merge
           {:method method
            :method-instance (get (:known-methods http-resource) method)
            :resource (:resource http-resource)
            :request request
            :allowed-methods (:allowed-methods http-resource)})

          (d/chain

           ;; Available?
           (link ctx
             (let [res (service/service-available? (:service-available? http-resource) ctx)]
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
             (when (service/request-uri-too-long? (:request-uri-too-long? options) (:uri request))
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
             (when-not (contains? (:allowed-methods http-resource) method)
               (d/error-deferred
                (ex-info "Method Not Allowed"
                         {:status 405
                          :headers {"allow" (str/join ", " (map (comp (memfn toUpperCase) name) (:allowed-methods http-resource)))}
                          ::http-response true}))))

           ;; Malformed? (parameters)
           (fn [ctx]
             (let [keywordize (fn [m] (into {} (for [[k v] m] [(keyword k) v])))

                   parameters (:parameters http-resource)

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
                            (rep/from-representation body (req/content-type request) schema)))

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

           ;; Authentication
           (fn [ctx]
             (cond-> ctx
               (not-empty (filter (comp (partial = :basic) :type) (:security http-resource)))
               (assoc :authentication (:basic-authentication (ring.middleware.basic-authentication/basic-authentication-request request (fn [user password] {:user user :password password}))))))

           ;; Authorization
           (fn [ctx]
             (if-let [res (service/authorize (:authorization http-resource) ctx)]
               (if (= res :not-authorized)
                 (d/error-deferred
                  (ex-info ""
                           (merge
                            {:status 401 ::http-response true}
                            (when-let [basic-realm (first (sequence realms-xf (:security http-resource)))]
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
              (res/fetch (:resource http-resource) ctx)
              (fn [res]
                (assoc ctx :resource res))))

           ;; TODO: Unknown or unsupported Content-* header

           ;; TODO: Request entity too large - shouldn't we do this later,
           ;; when we determine we actually need to read the request body?

           ;; Content negotiation
           ;; TODO: Unknown Content-Type? (incorporate this into conneg)
           (link ctx

             (let [representation
                   (rep/select-representation request (:representations http-resource))]

               (if (nil? representation)
                 (d/error-deferred (ex-info "" {:status 406
                                                ::http-response true}))

                 (cond-> ctx
                   representation (assoc-in [:response :representation] representation)
                   (:vary http-resource) (assoc-in [:response :vary] (:vary http-resource))
                   ))))

           ;; A current representation for the resource exists?
           (link ctx
             (if (:existence? http-resource)
               (d/chain
                (res/exists? (:resource ctx) ctx)
                (fn [exists?]
                  (assoc ctx :exists? exists?)))
               (assoc ctx :exists? true)))

           ;; Conditional requests - last modified time
           (fn [ctx]
             (d/chain
              (or
               (res/last-modified (:last-modified options) ctx)
               (res/last-modified (:resource ctx) ctx))

              (fn [last-modified]
                (if-let [last-modified (round-seconds-up last-modified)]

                  (if-let [if-modified-since (some-> request
                                                     (get-in [:headers "if-modified-since"])
                                                     ring.util.time/parse-date)]

                    (if (<=
                         (.getTime last-modified)
                         (.getTime if-modified-since))

                      ;; exit with 304
                      (d/error-deferred
                       (ex-info "" (merge {:status 304
                                           ::http-response true}
                                          ctx)))

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
           (link ctx

             (when-let [matches (some->> (get-in request [:headers "if-match"])
                                         (parse-csv)
                                         set)]

               ;; We have an If-Match to process
               (cond
                 (and (contains? matches "*") (pos? (count (:representations http-resource))))
                 ;; No need to compute etag, exit
                 ctx

                 ;; Otherwise we need to compute the etag for each current
                 ;; representation of the resource

                 ;; Create a map of representation -> etag
                 (:version? http-resource)
                 (let [version (res/version (:resource ctx) ctx)
                       etags (into {} (map (juxt identity (partial res/to-etag version)) (:representations http-resource)))]

                   (when (empty? (set/intersection matches (set (vals etags))))
                     (throw
                      (ex-info "Precondition failed"
                               {:status 412
                                ::http-response true})))

                   ;; Otherwise, let's use the etag we've just
                   ;; computed. Note, this might yet be
                   ;; overridden by the (unsafe) method returning
                   ;; a modified response. But if the method
                   ;; chooses not to reset the etag (perhaps the
                   ;; resource state didn't change), then this
                   ;; etag will do for the response.
                   (assoc-in ctx [:response :etag]
                             (get etags (:representation ctx)))))))

           ;; Methods
           (fn [ctx]
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

           (link ctx
             ;; only if resource supports etags
             (when (:version? http-resource)
               ;; only if the resource hasn't already set an etag
               (when-let [version (or (-> ctx :response :version)
                                      (res/version (:resource ctx) ctx))]
                 (let [etag (res/to-etag version (get-in ctx [:response :representation]))]
                   (assoc-in ctx [:response :etag] etag)))))

           ;; If we have just mutated the resource, we should
           ;; recompute the etag

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
                                (service/status (-> http-resource :options :status) ctx)
                                200)
                    :headers (merge
                              (get-in ctx [:response :headers])
                              ;; TODO: The context and its response
                              ;; map must be documented so users are
                              ;; clear what they can change and the
                              ;; effect of this change.
                              (when (not= method :options)
                                (merge {}
                                       (when-let [x (get-in ctx [:response :representation :content-type])]
                                         (let [y (get-in ctx [:response :representation :charset])]
                                           (if (and y (= (:type x) "text"))
                                             {"content-type" (mime/media-type->string (assoc-in x [:parameters "charset"] (charset/charset y)))}
                                             {"content-type" (mime/media-type->string x)})))
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
                              (service/headers (-> http-resource :options :headers) ctx))

                    ;; TODO :status and :headers should be implemented like this in all cases
                    :body (get-in ctx [:response :body])}]
               (debugf "Returning response: %s" (dissoc response :body))
               response)))

          ;; Handle exits
          (d/catch clojure.lang.ExceptionInfo
              #(let [data (ex-data %)]
                 (infof "Catching ex-info")

                 (if (::http-response data)
                   (if-let [debug-data (when debug (::debug data))]
                     (assoc data :body (prn-str debug-data))
                     data)

                   (throw (ex-info "Internal Server Error (ex-info)" data %))
                   #_{:status 500
                      :body (format "Internal Server Error: %s" (prn-str data))})))

          (d/catch #(identity
                     (throw (ex-info "Internal Server Error" {:request request} %))
                     #_{:status 500 :body
                        (html
                         [:body
                          [:h1 "Internal Server Error"]
                          [:p (str %)]
                          [:pre (with-out-str (apply str (interpose "\n" (seq (.getStackTrace %)))))]])}))))))

(defrecord HttpResource [resource id handler]
  clojure.lang.IFn
  (invoke [this req]
    (handle-request this req)))

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

(defn as-sequential [s]
  (if (sequential? s) s [s]))

(defn resource
  "Create a yada resource (Ring handler)"
  ([resource]                   ; Single-arity form with default options
   (yada.core/resource resource {}))

  ([resource options]
   (let [base resource

         resource (if (satisfies? res/ResourceCoercion resource)
                    (res/make-resource resource)
                    resource)

         known-methods (methods/known-methods)

         allowed-methods (conj
                          (set
                           (or (:allowed-methods options)
                               (res/allowed-methods resource)))
                          :head :options)

         parameters (or (:parameters options)
                        (when (satisfies? res/ResourceParameters resource)
                          (res/parameters resource)))

         representations (rep/representation-seq
                          (rep/coerce-representations
                           (or
                            (:representations options)
                            (when-let [rep (:representation options)] [rep])
                            (let [m (select-keys options [:content-type :charset :encoding :language])]
                              (when (not-empty m) [m]))
                            (when (satisfies? res/ResourceRepresentations resource)
                              (res/representations resource)))))

         vary (rep/vary representations)]

     (map->HttpResource
      {:id (or (:id options) (java.util.UUID/randomUUID))
       :resource resource
       :base base
       :options options
       :allowed-methods allowed-methods
       :known-methods known-methods
       :parameters parameters
       :representations representations
       :vary vary
       :security (as-sequential (:security options))
       :service-available? (:service-available? options)
       :authorization (or (:authorization options) (NoAuthorizationSpecified.))
       :version? (satisfies? res/ResourceVersion resource)
       :existence? (satisfies? res/ResourceExistence resource)
       }))))
