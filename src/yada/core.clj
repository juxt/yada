;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.walk :refer [postwalk]]
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
   [yada.representation :as rep]
   [yada.protocols :as p]
   [yada.response :refer [->Response]]
   [yada.resource :as resource]
   [yada.request-body :as rb]
   [yada.schema :as ys]
   [yada.service :as service]
   [yada.media-type :as mt]
   [yada.util :as util])
  (:import [java.util Date]))

#_(def CHUNK-SIZE 16384)

(defn merge-schemas [m]
  (let [p (:parameters m)]
    (assoc m :methods
           (reduce-kv
            (fn [acc k v]
              (assert (associative? v) (format "v is not associative: %s" v))
              (assoc acc k (update v :parameters (fn [lp] (merge p lp)))))
            {} (get m :methods {})))))

;; TODO: We are expanding shorthands using schema, but we must now
;; consider whether it is possible to use schema to also enact the
;; representation-seq on the result, or to do this 'manually'.

(defn expand-shorthands
  "Turns a resource into handler properties by expanding a
  human-author-friendly short-forms."
  ;; TODO: When structure is more stable, use coercions to achieve this
  ;; rather than postwalk. This has the benefit of being more formally
  ;; defined.
  [resource]
  (->> resource
       (postwalk
        (fn [x]
          (if (and (vector? x) (= (first x) :produces))
            [:produces (-> (second x)
                           rep/coerce-representations
                           rep/representation-seq)]
            x)))
       (postwalk
        (fn [x]
          (if (and (vector? x) (= (first x) :consumes))
            [:consumes (-> (second x)
                           rep/coerce-representations
                           rep/representation-seq)]
            x)))
       (merge-schemas)))

(defn make-context [properties]
  {:properties properties
   :response (->Response)})

;; TODO: Read and understand the date algo presented in RFC7232 2.2.1

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
  (if-not (:method-wrapper ctx)
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
      (methods/request (:method-wrapper ctx) ctx))
    ctx))

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

        schemas (get-in ctx [:handler :methods method :parameters])

        parameters {:path (if-let [schema (:path schemas)]
                            (rs/coerce schema (:route-params request) :query)
                            (:route-params request))
                    :query (let [qp (:query-params (assoc-query-params request (or (:charset ctx) "UTF-8")))]
                             (if-let [schema (:query schemas)]
                               (let [coercer (sc/coercer schema
                                                         (or
                                                          coerce/+parameter-key-coercions+
                                                          (rsc/coercer :query)
                                                          ))]
                                 (coercer qp))
                               qp
                               ))}



        #_parameters #_(-> ctx :handler :parameters)

        #_parameters
        #_(when parameters
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
        (assoc ctx :parameters (util/remove-nil-vals parameters))))))

(defn safe-read-content-length [req]
  (let [len (get-in req [:headers "content-length"])]
    (when len
      (try
        (Long/parseLong len)
        (catch Exception e
          (throw (ex-info "Malformed Content-Length" {:value len})))))))

(defn process-request-body
  "Process the request body, if necessary. RFC 7230 section 3.3 states
  \"The presence of a message body in a request is signaled by a
  Content-Length or Transfer-Encoding header field. Request message
  framing is independent of method semantics, even if the method does
  not define any use for a message body\".

  Therefore we process the request body if the request contains a
  Content-Length or Transfer-Encoding header, regardless of the method
  semantics."
  [{:keys [request] :as ctx}]
  (if (-> request :headers (filter #{"content-length" "transfer-encoding"}) not-empty)
    (let [content-type (mt/string->media-type (get-in request [:headers "content-type"]))
          content-length (safe-read-content-length request)]

      ;; TODO: Check if we consume...
      (infof "content-type is %s" (:name content-type))
      (infof "content-length is %s" content-length)

      (cond-> ctx
        ;; TODO: Check options to see if we have a maximum requested entity size, default is no.
        (and content-length (pos? content-length))
        (rb/process-request-body (stream/map bs/to-byte-array (:body request)) content-type)))

    ;; else
    ctx))

#_(defn process-request-body [ctx]
  ;; Only read the body if the parameters include :form or :body,
  ;; otherwise leave it available for method implementations. No! Can't
  ;; do this, because a method implementation cannot be asynchronous. It
  ;; must indicate its body processing expectation via the :parameters
  ;; declaration. Yes! can do this because a method implementation /can/
  ;; be asynchronous, it can return a d/chain which contains a d/loop

  ;; TODO: Request entity too large - need to indicate this in parameters

  ;; Let's try to read the request body
  (let [method (:method ctx)
        request (:request ctx)
        parameters (-> ctx :handler :parameters)

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
                request-buffer-size CHUNK-SIZE ; as Aleph default, TODO: derive this
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
          (let [coercer (get-in coercers [method parameter-key])
                bufs (:body request)]

            (d/chain
             (stream/reduce (fn [acc buf] (conj acc buf))
                            [] bufs)
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
              request-buffer-size CHUNK-SIZE ; as Aleph default
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

(defn- update-produces [props ])

(defn get-properties
  [ctx]
  (let [propsfn (get-in ctx [:handler :properties] (constantly {}))]
    (d/chain
     (propsfn ctx)                     ; propsfn can returned a deferred
     (fn [props]
       (let [props (expand-shorthands props)]
         (cond-> (assoc ctx :properties props)
           (:produces props) (assoc-in [:response :vary] (rep/vary (:produces props))))))
     (fn [ctx]
       (infof "Result from get-properties is %s" (:properties ctx))
       ctx
       )
     )))


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

(defn select-representation
  [ctx]
  ;; TODO: Need metadata to say whether the :produces property 'replaces'
  ;; or 'augments' the static produces declaration. Currently only
  ;; 'replaces' is supported.
  (let [produces (or (get-in ctx [:properties :produces])
                     (concat (get-in ctx [:handler :methods (:method ctx) :produces])
                             (get-in ctx [:handler :produces])))
        rep (rep/select-best-representation (:request ctx) produces)]
    (cond-> ctx
      produces (assoc :produces produces)
      rep (assoc-in [:response :produces] rep)
      (and rep (-> ctx :handler :vary)) (assoc-in [:response :vary] (-> ctx :handler :vary)))))

;; Conditional requests - last modified time
(defn check-modification-time [ctx]
  (if-let [last-modified
           (or
            (-> ctx :options :last-modified)
            (-> ctx :properties :last-modified))]

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
    ctx))

;; Check ETag - we already have the representation details,
;; which are necessary for a strong validator. See
;; section 2.3.3 of RFC 7232.

;; If-Match check
(defn if-match
  [ctx]

  (if-let [matches (some->> (get-in (:request ctx) [:headers "if-match"]) util/parse-csv set)]

    ;; We have an If-Match to process
    (cond
      (and (contains? matches "*") (-> ctx :produces count pos?))
      ;; No need to compute etag, exit
      ctx

      ;; Otherwise we need to compute the etag for each current
      ;; representation of the resource

      ;; Create a map of representation -> etag
      (-> ctx :properties :version)
      (let [version (-> ctx :properties :version)
            etags (into {}
                        (for [rep (:produces ctx)]
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
                    (get etags (:produces ctx))))))
    ctx))

;; If-None-Match check
(defn if-none-match
  [ctx]

  (if-let [matches (some->> (get-in (:request ctx) [:headers "if-none-match"]) util/parse-csv set)]

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
                        (for [rep (:produces ctx)]
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
  (methods/request (:method-wrapper ctx) ctx))


#_(let [propsfn (get-in ctx [:handler :properties] (constantly {}))]
    (d/chain

     (propsfn ctx)                     ; propsfn can returned a deferred

     (fn [props]
       (assoc
        ctx
        :properties
        (cond-> props
          (:produces props) ; representations shorthand is expanded
          (update-in [:produces]
                     (comp rep/representation-seq rep/coerce-representations)))))))
(defn get-new-properties
  "If the method is unsafe, call properties again. This will
  pick up any changes that are used in subsequent interceptors, such as
  the new version of the resource."
  [ctx]
  (let [resource (:resource ctx)]
    (if (not (methods/safe? (:method-wrapper ctx)))

      (let [propsfn (get-in ctx [:handler :properties] (constantly {}))]
        (d/chain

         (propsfn ctx)                 ; propsfn can returned a deferred

         (fn [props]
           (assoc
            ctx
            :new-properties
            (cond-> props
              (:produces props)  ; representations shorthand is
                                 ; expanded, is this necessary at this
                                 ; stage?
              (update-in [:produces]
                         (comp rep/representation-seq rep/coerce-representations)))))))


      ;; Old code to indicate how to do schema validation/coercion on properties
      #_(d/chain
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
    (let [etag (p/to-etag version (get-in ctx [:response :produces]))]
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
                            (when-let [x (get-in ctx [:response :produces :media-type])]
                              (let [y (get-in ctx [:response :produces :charset])]
                                (if (and y (= (:type x) "text"))
                                  {"content-type" (mt/media-type->string (assoc-in x [:parameters "charset"] (charset/charset y)))}
                                  {"content-type" (mt/media-type->string x)})))
                            (when-let [x (get-in ctx [:response :produces :encoding])]
                              {"content-encoding" x})
                            (when-let [x (get-in ctx [:response :produces :language])]
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
        id (java.util.UUID/randomUUID)
        error-handler (or (:error-handler options)
                          default-error-handler)
        ctx (merge
             (make-context (:properties handler))
             {:id id
              :method method
              :method-wrapper (get (:known-methods handler) method)
              :interceptor-chain interceptor-chain
              :handler handler
              :resource (:resource handler)
              :request request
              :allowed-methods (:allowed-methods handler)
              :options options})]

    (->
     (apply d/chain ctx interceptor-chain)

     (d/catch
         clojure.lang.ExceptionInfo
         (fn [e]
           (error-handler e)
           (let [data (error-data e)]
             (let [status (or (:status data) 500)
                   rep (rep/select-best-representation
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
                  (not (:body data)) ((fn [ctx]
                                        (let [b (body/to-body (body/render-error status e rep ctx) rep)]
                                          (-> ctx
                                              (assoc-in [:response :body] b)
                                              (assoc-in [:response :headers "content-length"] (body/content-length b))))))

                  rep (assoc-in [:response :produces] rep))
                create-response))))))))

(defrecord Handler []
  clojure.lang.IFn
  (invoke [this req]
    (handle-request this req))

  ;; see new-custom-resource, want this for meta-yada, i.e. (-> x yada yada yada)
  #_p/Properties
  #_(properties
      [this]
      {:allowed-methods #{:get}
       :representations [{:media-type #{"text/html"
                                        "application/edn"
                                        "application/json"
                                        "application/edn;pretty=true"
                                        "application/json;pretty=true"}}]})

  #_(properties [_ ctx] {})
  #_methods/Get
  #_(GET [this ctx] (into {} this)))

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

(defn yada
  "Create a Ring handler"
  ([resource]                   ; Single-arity form with default options
   (yada resource {}))

  ([resource options]
   (let [base resource

         resource (if (satisfies? p/ResourceCoercion resource)
                    (p/as-resource resource)
                    resource)

         ;; Validate the resource structure, with coercion if
         ;; necessary.
         resource (ys/resource-coercer resource)
         _ (assert (not (schema.utils/error? resource)) (pr-str resource))

         ;; This handler services a collection of resources
         ;; (TODO: this is ambiguous, what do we mean exactly?)
         ;; See yada.resources.file-resource for an example.
         collection? (or (:collection? options)
                         (:collection? resource))

         known-methods (methods/known-methods)


         allowed-methods (let [methods (set
                                        (or (:allowed-methods options)
                                            (keys (:methods resource))))]
                           (cond-> methods
                             (some #{:get} methods) (conj :head)
                             true (conj :options)))

         ;; The point of calculating the coercers here is that
         ;; parameters are wholly static (if schema checking is desired,
         ;; non-declarative (dynamic, runtime) implementations can do
         ;; their own dynamical checking!). As such, we want to
         ;; pre-calculate them here rather than on every request.
         #_parameter-coercers
         #_(->> (for [[method schemas] parameters]
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

         #_representations #_(rep/representation-seq
                              (rep/coerce-representations
                               (or
                                (get :produces properties)
                                ;; Default
                                [{}])))

         vary (when-let [produces (:produces resource)]
                (infof "static produces is %s" (pr-str produces))
                (rep/vary produces))

         _ (infof "static vary is %s" vary)

         ]

     (when-not (map? resource)
       (throw (ex-info "Resource is not a map" {:resource resource
                                                :type (type resource)})))

     (map->Handler

      (merge {
              :id (or (:id options) (java.util.UUID/randomUUID))

              :base base
              :resource resource
              :options options

              :allowed-methods allowed-methods
              :known-methods known-methods

              :interceptor-chain default-interceptor-chain

              ;;        :parameters parameters
              ;;        :representations representations
              ;;        :properties properties
              :vary vary

              :collection? collection?
              }

             resource

             )))))
