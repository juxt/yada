;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [byte-streams :as bs]
   [bidi.bidi :as bidi]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   ring.middleware.basic-authentication
   [ring.middleware.params :refer [assoc-query-params]]
   [ring.swagger.schema :as rs]
   [ring.swagger.coerce :as rsc]
   ring.util.codec
   [ring.util.request :as req]
   ring.util.time
   schema.utils
   [schema.core :as s]
   [schema.coerce :as sc]
   [yada.body :as body]
   [yada.charset :as charset]
   [yada.coerce :as coerce]
   [yada.journal :as journal]
   [yada.methods :as methods]
   [yada.multipart :refer [parse-multipart reduce-piece ->DefaultPartReceiver xf-add-header-info xf-parse-content-disposition]]
   [yada.representation :as rep]
   [yada.protocols :as p]
   [yada.response :refer [->Response]]
   [yada.resource :as resource]
   [yada.request-body :as request-body]
   [yada.service :as service]
   [yada.media-type :as mt]
   [yada.util :refer [parse-csv remove-nil-vals]])
  (:import [java.util Date]))

(defn make-context [properties]
  {:properties properties
   :response (->Response)})

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

(def realms-xf
  (comp
   (filter (comp (partial = :basic) :type))
   (map :realm)))

(defn available?
  "Is the service available?"
  [ctx]
  (let [res (service/service-available? (-> ctx :handler :options :service-available?) ctx)]
    (if-not (service/interpret-service-available res)
      (d/error-deferred
       (ex-info "" (merge {:status 503}
                          (when-let [retry-after (service/retry-after res)] {:headers {"retry-after" retry-after}}))))
      ctx)))

(defn known-method?
  [ctx]
  (if-not (:method-instance ctx)
    (d/error-deferred (ex-info "" {:status 501 ::method (:method ctx)}))
    ctx))

(defn uri-too-long?
  [ctx]
  (if (service/request-uri-too-long? (-> ctx :options :request-uri-too-long?) (-> ctx :request :uri))
    (d/error-deferred (ex-info "" {:status 414}))
    ctx))

(defn TRACE
  [ctx]
  (if (#{:trace} (:method ctx))
    (if (false? (-> ctx :options :trace))
      (d/error-deferred
       (ex-info "Method Not Allowed"
                {:status 405}))
      (methods/request (:method-instance ctx) ctx))
    ctx))

(defn get-properties
  [ctx]
  (let [resource (:resource ctx)]
    (d/chain
     (resource/properties-on-request resource ctx)

     ;; TODO: It is now illegal to return :parameters from
     ;; properties-on-request because it's TOO LATE. parse-parameters
     ;; has already been called. Ensure that the resource's
     ;; properties-on-request is not attempting to return :parameters

     (fn [props]
       ;; Canonicalize possible representations if they are reasserted.
       (cond-> props
         (:representations props)
         (update-in [:representations]
                    (comp rep/representation-seq rep/coerce-representations))))
     (fn [props]
       (if (schema.utils/error? props)
         (d/error-deferred
          ;; TODO: More thorough error handling
          ;; TODO: Test me!
          (ex-info "Internal Server Error"
                   {:status 500
                    :error props}))

         (-> ctx
             (assoc-in [:representations] (or (:representations props)
                                              (-> ctx :handler :representations)))
             (update-in [:properties] merge props)))))))

(defn method-allowed?
  "Is method allowed on this resource?"
  [ctx]
  (if-not (contains? (-> ctx :handler :allowed-methods) (:method ctx))
    (d/error-deferred
     (ex-info "Method Not Allowed"
              {:status 405
               :headers {"allow" (str/join ", " (map (comp (memfn toUpperCase) name) (-> ctx :handler :allowed-methods)))}}))

    ctx))

(defn parse-parameters
  "Parse request and coerce parameters."
  [ctx]
  (let [method (:method ctx)
        request (:request ctx)

        parameters (-> ctx :handler :parameters)
        coercers (-> ctx :handler :parameter-coercers)

        parameters
        (when parameters
          {:path
           (when-let [schema (get-in parameters [method :path])]
             (rs/coerce schema (:route-params request) :query))

           :query
           (when-let [coercer (get-in coercers [method :query])]
             ;; We'll call assoc-query-params with the negotiated charset, falling back to UTF-8.
             ;; Also, read this:
             ;; http://www.w3.org/TR/html5/forms.html#application/x-www-form-urlencoded-encoding-algorithm

             (coercer
              (-> request (assoc-query-params (or (:charset ctx) "UTF-8")) :query-params)))

           #_:form
           #_(cond
             (and (req/urlencoded-form? request) (get-in coercers [method :form]))
             (when-let [coercer (get-in coercers [method :form])]
               (coercer (ring.util.codec/form-decode (read-body (-> ctx :request))
                                                     (req/character-encoding request)))))

           #_:body
           #_(when-let [schema (get-in parameters [method :body])]
             (let [body (read-body (-> ctx :request))]
               (body/coerce-request-body
                body
                ;; See rfc7231#section-3.1.1.5 - we should assume application/octet-stream
                (or (req/content-type request) "application/octet-stream")
                schema)))

           :header
           (when-let [schema (get-in parameters [method :header])]
             (let [params (-> request :headers)]
               (rs/coerce (assoc schema String String) params :query)))})]

    (let [errors (filter (comp schema.utils/error? second) parameters)]
      (if (not-empty errors)
        (d/error-deferred (ex-info "" {:status 400
                                       :errors errors}))
        (assoc ctx :parameters (remove-nil-vals parameters))))))

(defn process-request-body [ctx]
  ;; Only read the body if the parameters include :form or :body,
  ;; otherwise leave it available for method implementations. No! Can't
  ;; do this, because a method implementation cannot be asynchronous. It
  ;; must indicate its body processing expectation via the :parameters
  ;; declaration. Yes! can do this because a method implementation /can/
  ;; be asynchronous, it can return a d/chain which contains a d/loop

  ;; TODO: Unknown or unsupported Content-* header
  ;; TODO: Request entity too large - need to indicate this in parameters

  ;; Let's try to read the request body
  (let [method (:method ctx)
        request (:request ctx)
        parameters (-> ctx :handler :parameters)
        coercers (-> ctx :handler :parameter-coercers)

        ;; TODO: What if content-type is malformed?
        content-type (mt/string->media-type (get-in request [:headers "content-type"]))
        parameter-key (cond (some? (get-in parameters [method :body])) :body
                            (some? (get-in parameters [method :form])) :form)]

    (if-not parameter-key
      ctx       ; defer body processing to method implementation, unless
                                        ; there's a parameter declaration.
      (let [required-type (get-in parameters [method parameter-key])]
        (cond
          ;; multipart/form-data
          (and (= (:name content-type) "multipart/form-data")
               (map? required-type))
          (let [boundary (get-in content-type [:parameters "boundary"])
                request-buffer-size 16384 ; as Aleph default, TODO: derive this
                window-size (* 4 request-buffer-size)]
            (d/chain
             (->> (parse-multipart boundary window-size request-buffer-size (:body request))
                  (stream/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))
                  (stream/reduce
                   reduce-piece
                   { ;; We could support other ways of assembling 'large'
                    ;; parts. This default implementation uses memory but
                    ;; other strategies could be brought in.
                    :receiver (->DefaultPartReceiver)
                    ;; TODO: Would be nice not to have to specify the
                    ;; empty vector here
                    :state {:parts []}}))
             (fn [body]
               (if body
                 (-> ctx
                     (assoc :body :processed)
                     (assoc-in [:parameters parameter-key]
                               (let [params (into {}
                                                  (map
                                                   (juxt #(get-in % [:content-disposition :params "name"])
                                                         (fn [part]
                                                           (let [offset (get part :body-offset 0)]
                                                             (String. (:bytes part) offset (- (count (:bytes part)) offset)))))
                                                   (filter #(= (:type %) :part) (:parts body))))]
                                 (if-let [schema (get-in parameters [method parameter-key])]
                                   ;; ?? Don't we have coercers already in place?
                                   (let [params ((sc/coercer schema
                                                             (or
                                                              coerce/+parameter-key-coercions+
                                                              (rsc/coercer :json))) params)]

                                     (if (schema.utils/error? params)
                                       (throw (ex-info "Unexpected body" {:status 400}))
                                       params))
                                   params))))))))

          (and (= (:name content-type) "application/x-www-form-urlencoded")
               (get-in coercers [method parameter-key]))
          (let [coercer (get-in coercers [method parameter-key])]
            (d/chain
             (stream/reduce (fn [acc buf] (conj acc buf)) [] (:body request))
             (fn [bufs]
               (let [cs (req/character-encoding request)]
                 (-> ctx
                     (assoc :body :processed)
                     (assoc-in [:parameters parameter-key]
                               (coercer (ring.util.codec/form-decode
                                         (apply bs/to-string bufs
                                                (if cs [{:encoding cs}] []))
                                         cs))))))))

          (= required-type String)
          ;; Return a deferred which will consume the request body,
          ;; finally turning the contents into a String
          (d/chain
           (stream/reduce (fn [acc buf] (conj acc buf)) [] (:body request))
           (fn [bufs]
             (-> ctx
                 (assoc :body :processed)
                 (assoc-in [:parameters parameter-key]
                           (apply bs/to-string bufs
                                  (if-let [cs (req/character-encoding request)]
                                    [{:encoding cs}]
                                    []))))))

          :otherwise ctx
          )))


    ;; However, (:body ctx) is going to be the request body

    ;; First process the body. Under certain circumstances, (content
    ;; type is application/json or application/edn), we can coerce, and
    ;; the result can be merged into (:parameters ctx)

    ;; Going into request body processing logic we have
    ;; a) the request's declared content-type
    ;; b) the server's expectation, declared in schema

    ;; For now, we'll simply process the body as per the declared Content-Type.
    ;; We assume the body will be chunked.

    #_(if-let [schema (get-in parameters [method :body])]
        (rs/coerce schema (:route-params request) :query))

    #_(when-let [schema (get-in parameters [method :body])]
        (let [body (read-body (-> ctx :request))]
          (body/coerce-request-body
           body
           ;; See rfc7231#section-3.1.1.5 - we should assume application/octet-stream
           (or (req/content-type request) "application/octet-stream")
           schema)))

    #_(when parameters
        {
         #_:form
         #_(cond
             (and (req/urlencoded-form? request) (get-in coercers [method :form]))
             (when-let [coercer (get-in coercers [method :form])]
               (coercer (ring.util.codec/form-decode (read-body (-> ctx :request))
                                                     (req/character-encoding request)))))

         #_:body
         #_(when-let [schema (get-in parameters [method :body])]
             (let [body (read-body (-> ctx :request))]
               (body/coerce-request-body
                body
                ;; See rfc7231#section-3.1.1.5 - we should assume application/octet-stream
                (or (req/content-type request) "application/octet-stream")
                schema)))
         }))

  #_(let [mt (mt/string->media-type (get-in (:request ctx) [:headers "content-type"]))]

      (case (:name mt)
        "multipart/form-data"           ; special processing required
        (let [boundary (get-in mt [:parameters "boundary"])
              request-buffer-size 16384 ; as Aleph default
              window-size (* 4 request-buffer-size)]

          ;; TODO: If boundary is malformed, throw a 400
          (d/chain
           (->> (parse-multipart boundary window-size request-buffer-size (:body (:request ctx)))
                (stream/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))

                (stream/reduce reduce-piece {:receiver (->DefaultPartReceiver)
                                             ;; TODO: Would be nice not to
                                             ;; have to specify the empty
                                             ;; vector here
                                             :state {:parts []}}))

           (fn [parts]
             (assoc ctx :body parts))))



        ;; Otherwise
        ctx)))

#_(defn authentication
  "Authentication"
  [ctx]
  (cond-> ctx
    (not-empty (filter (comp (partial = :basic) :type) (-> ctx :handler :security)))
    (assoc :authentication
           (:basic-authentication (ring.middleware.basic-authentication/basic-authentication-request
                                   (:request ctx)
                                   (fn [user password] {:user user :password password}))))))

#_(defn authorization
  "Authorization"
  [ctx]
  (if-let [res (service/authorize (-> ctx :handler :authorization) ctx)]
    (if (= res :not-authorized)
      (d/error-deferred
       (ex-info ""
                (merge
                 {:status 401}
                 (when-let [basic-realm (first (sequence realms-xf (-> ctx :handler :security)))]
                   {:headers {"www-authenticate" (format "Basic realm=\"%s\"" basic-realm)}}))))

      (if-let [auth (service/authorization res)]
        (assoc ctx :authorization auth)
        ctx))

    (d/error-deferred (ex-info "" {:status 403}))))

;; Content negotiation
;; TODO: Unknown Content-Type? (incorporate this into conneg)
(defn select-representation
  [ctx]
  (let [representation
        (rep/select-representation (:request ctx) (:representations ctx))]

    (cond-> ctx
      representation
      (assoc-in [:response :representation] representation)

      (and representation (-> ctx :handler :vary))
      (assoc-in [:response :vary] (-> ctx :handler :vary)))))

;; Conditional requests - last modified time
(defn check-modification-time [ctx]
  (d/chain
   (or
    (-> ctx :options :last-modified)
    (-> ctx :properties :last-modified))

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
            (ex-info "" (merge {:status 304} ctx)))

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
      (and (contains? matches "*") (-> ctx :representations count pos?))
      ;; No need to compute etag, exit
      ctx

      ;; Otherwise we need to compute the etag for each current
      ;; representation of the resource

      ;; Create a map of representation -> etag
      (-> ctx :properties :version)
      (let [version (-> ctx :properties :version)
            etags (into {}
                        (for [rep (:representations ctx)]
                          [rep (p/to-etag version rep)]))]

        (if (empty? (set/intersection matches (set (vals etags))))
          (d/error-deferred
           (ex-info "Precondition failed"
                    {:status 412}))

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

;; If-None-Match check
(defn if-none-match
  [ctx]

  (if-let [matches (some->> (get-in (:request ctx) [:headers "if-none-match"]) parse-csv set)]

    ;; TODO: Weak comparison. Since we don't (yet) support the issuance
    ;; of weak entity tags, weak and strong comparison are identical
    ;; here.

    (if
        ;; Create a map of representation -> etag. This was done in
        ;; if-match, but it is unlikely that we have if-match and
        ;; if-none-match in the same request, so a performance
        ;; optimization is unwarranted.
        (-> ctx :properties :version)
      (let [version (-> ctx :properties :version)
            etags (into {}
                        (for [rep (:representations ctx)]
                          [rep (p/to-etag version rep)]))]

        (when (not-empty (set/intersection matches (set (vals etags))))
          (d/error-deferred
           (ex-info ""
                    {:status 304}))

          )))
    ctx))

(defn invoke-method
  "Methods"
  [ctx]
  (methods/request (:method-instance ctx) ctx))

(defn get-new-properties
  "If the method is unsafe, call properties again. This will
  pick up any changes that are used in subsequent interceptors, such as
  the new version of the resource."
  [ctx]
  (let [resource (:resource ctx)]
    (if (not (methods/safe? (:method-instance ctx)))
      (d/chain
       (try
         (resource/properties-on-request resource ctx)
         (catch AbstractMethodError e {}))
       (fn [props]
         (if (schema.utils/error? props)
           (d/error-deferred
            (ex-info "Internal Server Error"
                     {:status 500}))
           (assoc ctx :new-properties props))))
      ctx)))

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
  (if-let [version (or
                    (-> ctx :new-properties :version)
                    (-> ctx :properties :version))]
    (let [etag (p/to-etag version (get-in ctx [:response :representation]))]
      (assoc-in ctx [:response :etag] etag))
    ctx))

(defn access-control-headers [ctx]
  (let [allow-origin (get-in ctx [:options :access-control :allow-origin])
        interpreted-allow-origin (when allow-origin (service/allow-origin allow-origin ctx))
        expose-headers (get-in ctx [:options :access-control :expose-headers])
        allow-headers (get-in ctx [:options :access-control :allow-headers])]

    (cond-> ctx
      interpreted-allow-origin
      (assoc-in [:response :headers "access-control-allow-origin"] interpreted-allow-origin)

      expose-headers
      (assoc-in [:response :headers "access-control-expose-headers"]
                (apply str
                       (interpose ", " expose-headers)))

      allow-headers
      (assoc-in [:response :headers "access-control-allow-headers"]
                (apply str
                       (interpose ", " allow-headers))))))

;; Response
(defn create-response
  [ctx]
  (let [response
        {:status (or (get-in ctx [:response :status])
                     (service/status (-> ctx :handler :options :status) ctx)
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
                   (service/headers (-> ctx :handler :options :headers) ctx))

         ;; TODO :status and :headers should be implemented like this in all cases
         :body (get-in ctx [:response :body])}]
    (debugf "Returning response: %s" (dissoc response :body))
    response))

(defn wrap-journaling [journal-entry]
  (fn [interceptor]
    (fn [ctx]
      (let [t0 (System/nanoTime)]
        (d/chain
         (interceptor ctx)
         (fn [output]
           (let [t1 (System/nanoTime)]
             (swap! journal-entry
                    update-in [:chain] conj {:interceptor interceptor
                                             :old (select-keys ctx [:response])
                                             :new (cond
                                                    (instance? yada.response.Response output) output
                                                    :otherwise (select-keys output [:response]))
                                             :t0 t0 :t1 t1
                                             :duration (- t1 t0)})
             output)))))))

(defn error-data
  [e]
  (cond
    (instance? clojure.lang.ExceptionInfo e) (ex-data e)
    (instance? java.lang.Throwable e) nil
    :else e))

(defn default-error-handler [e]
  (let [data (error-data e)]
    (when-not (and (:status data) (< (:status data) 500))
      (when (instance? java.lang.Throwable e)
        (errorf e "Internal Error %s" (or (some-> data :status str) "")))
      (when data (errorf "ex-data: %s" data)))))

(defn- handle-request
  "Handle Ring request"
  [handler request]
  (let [method (:request-method request)
        interceptor-chain (:interceptor-chain handler)
        options (:options handler)
        journal-entry (atom {:chain []})
        id (java.util.UUID/randomUUID)
        error-handler (or (:error-handler options)
                          default-error-handler)
        ctx (merge
             (make-context (:properties handler))
             {:id id
              :method method
              :method-instance (get (:known-methods handler) method)
              :interceptor-chain interceptor-chain
              :handler handler
              :resource (:resource handler)
              :request request
              :allowed-methods (:allowed-methods handler)
              :options options
              :journal journal-entry})]

    (->
     (->> interceptor-chain
          (mapv (wrap-journaling journal-entry))
          (apply d/chain ctx))

     (d/catch
         java.lang.Object
         (fn [e]
           (error-handler e)
           (let [data (error-data e)]
             (do
               (when-let [journal (:journal handler)]
                 (swap! journal assoc id (swap! journal-entry assoc :error {:exception e :data data})))

               (let [status (or (:status data) 500)
                     rep (rep/select-representation
                          (:request ctx)
                          (rep/representation-seq
                           (rep/coerce-representations
                            ;; Possibly in future it will be possible
                            ;; to support more media-types to render
                            ;; errors, including image and video
                            ;; formats.
                            [{:media-type #{"text/plain"
                                            "text/html;q=0.8"
                                            "application/json;q=0.75"
                                            "application/json;pretty=true;q=0.7"
                                            "application/edn;q=0.6"
                                            "application/edn;pretty=true;q=0.5"}
                              :charset charset/platform-charsets}])))]

                 ;; TODO: Custom error handlers

                 (d/chain
                  (cond-> (make-context {})
                    status (assoc-in [:response :status] status)
                    (:headers data) (assoc-in [:response :headers] (:headers data))
                    (not (:body data)) (assoc-in [:response :body]
                                                 (body/to-body (body/render-error status e rep ctx) rep))

                    rep (assoc-in [:response :representation] rep))
                  create-response))
               )))))))

(defrecord Handler
    [id
     resource
     base
     interceptor-chain
     options
     allowed-methods
     known-methods
     parameters
     representations
     vary
     #_security
     service-available?
     #_authorization
     journal]
  clojure.lang.IFn
  (invoke [this req]
    (handle-request this req))
  p/Properties
  (properties
    [this]
    {:allowed-methods #{:get}
     :representations [{:media-type #{"text/html"
                                      "application/edn"
                                      "application/json"
                                      "application/edn;pretty=true"
                                      "application/json;pretty=true"}}]})

  (properties [_ ctx] {})
  methods/Get
  (GET [this ctx] (into {} this)))

(defrecord NoAuthorizationSpecified []
  service/Service
  (authorize [b ctx] true)
  (authorization [_] nil))

(def default-interceptor-chain
  [available?
   known-method?
   uri-too-long?
   TRACE

   method-allowed?
   parse-parameters

   process-request-body

   get-properties

;;   authentication
;;   authorization

   check-modification-time

   select-representation
   ;; if-match and if-none-match computes the etag of the selected representations, so
   ;; needs to be run after select-representation
   if-match
   if-none-match

   invoke-method
   get-new-properties
   compute-etag
   access-control-headers
   create-response
   ])

(defn handler
  "Create a Ring handler"
  ([resource]                   ; Single-arity form with default options
   (yada.core/handler resource {}))

  ([resource options]
   (let [base resource

         resource (if (satisfies? p/ResourceCoercion resource)
                    (p/as-resource resource)
                    resource)

         properties (if (satisfies? p/Properties resource)
                      (resource/properties resource)
                      resource/default-properties)

         ;; This handler services a collection of resources
         collection? (or (:collection? options)
                         (:collection? properties))

         known-methods (methods/known-methods)

         allowed-methods (or
                          ;; TODO: Test for this
                          (when-let [methods (or (:all-allowed-methods options)
                                                 (:all-allowed-methods properties))]
                            (set methods))
                          (conj
                           (set
                            (or (:allowed-methods options)
                                (:allowed-methods properties)
                                (methods/infer-methods resource)))
                           :head :options))

         parameters (or (:parameters options)
                        (:parameters properties))

         parameter-coercers
         (->> (for [[method schemas] parameters]
                [method
                 (merge
                  (when-let [schema (:query schemas)]
                    {:query (when-let [schema (:query schemas)]
                              (sc/coercer schema
                                          (or
                                           coerce/+parameter-key-coercions+
                                           (rsc/coercer :query)
                                           )))})
                  (when-let [schema (:form schemas)]
                    {:form (when-let [schema (:form schemas)]
                             (sc/coercer schema
                                         (or
                                          coerce/+parameter-key-coercions+
                                          (rsc/coercer :json)
                                          )))}))])
              (filter (comp not nil? second))
              (into {}))

         representations (rep/representation-seq
                          (rep/coerce-representations
                           (or
                            (:representations options)
                            (when-let [rep (:representation options)] [rep])
                            (let [m (select-keys options [:media-type :charset :encoding :language])]
                              (when (not-empty m) [m]))
                            (:representations properties)
                            ;; Default
                            [{}])))

         vary (rep/vary representations)

         journal (:journal options)
         ]

     (map->Handler
      (merge
       {:allowed-methods allowed-methods
        ;;        :authorization (or (:authorization options) (NoAuthorizationSpecified.))
        :base base
        :id (or (:id options) (java.util.UUID/randomUUID))
        :interceptor-chain default-interceptor-chain
        :known-methods known-methods
        :options options
        :parameters parameters
        :parameter-coercers parameter-coercers
        :representations representations
        :resource resource
        :properties properties

        :collection? collection?

        ;;        :security (as-sequential (:security options))
        :vary vary}
       (when journal {:journal journal}))))))

(def yada handler)

(def ^:deprecated resource handler)
