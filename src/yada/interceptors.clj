;; Copyright © 2015, JUXT LTD.

(ns yada.interceptors
  (:require
   [byte-streams :as bs]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   [ring.middleware.params :refer [assoc-query-params]]
   [ring.swagger.coerce :as rsc]
   [ring.swagger.schema :as rs]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :refer [error?]]
   [yada.body :as body]
   [yada.charset :as charset]
   [yada.coerce :as coerce]
   [yada.cookies :as cookies]
   [yada.media-type :as mt]
   [yada.methods :as methods]
   [yada.multipart]
   [yada.protocols :as p]
   [yada.request-body :as rb]
   [yada.representation :as rep]
   [yada.schema :as ys]
   [yada.util :as util]))

(defn available?
  "Is the service available?"
  [ctx]
  ctx
  #_(let [res (service/service-available? (-> ctx :handler :service-available?) ctx)]
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
  ctx
  #_(if (service/request-uri-too-long? (-> ctx :request-uri-too-long?)
                                     (-> ctx :request :uri))
    (d/error-deferred (ex-info "" {:status 414}))
    ctx))

(defn TRACE
  [ctx]
  (assert ctx "TRACE, ctx is nil!")
  (if (#{:trace} (:method ctx))
    (methods/request (:method-wrapper ctx) ctx)
    ctx))

(defn method-allowed?
  "Is method allowed on this resource?"
  [ctx]
  (assert ctx "method-allowed?, ctx is nil!")
  (if (or (contains? (:allowed-methods ctx) (:method ctx))
          (some-> ctx :resource :methods :*))
    ctx
    (d/error-deferred
     (ex-info "Method Not Allowed"
              {:status 405
               :headers {"allow"
                         (str/join ", "
                                   (map (comp (memfn toUpperCase) name)
                                        (:allowed-methods ctx)))}}))
    ))

(defn capture-cookies [ctx]
  (if-let [cookies (cookies/parse-cookies (:request ctx))]
    (assoc ctx :cookies cookies)
    ctx))

(defn parse-parameters
  "Parse request and coerce parameters. Capture cookies."
  [ctx]
  (assert ctx "parse-parameters, ctx is nil!")

  (let [ctx (capture-cookies ctx)
        method (:method ctx)
        request (:request ctx)

        schemas (util/merge-parameters (get-in ctx [:resource :parameters])
                                       (get-in ctx [:resource :methods method :parameters]))

        ;; TODO: Creating coercers on every request is unnecessary and
        ;; expensive, should pre-compute them.

        parameters
        {:path (if-let [schema (:path schemas)]
                 (rs/coerce schema (:route-params request) :query)
                 (:route-params request))
         :query (let [qp (:query-params (assoc-query-params request (or (:charset ctx) "UTF-8")))]
                  (if-let [schema (:query schemas)]
                    (let [coercer (sc/coercer schema
                                              (or
                                               coerce/+parameter-key-coercions+
                                               (rsc/coercer :query)))]
                      (coercer qp))
                    qp))
                    
         :header (when-let [schema (:header schemas)]
                   ;; Allow any other headers
                   (let [coercer (sc/coercer (merge schema {s/Str s/Str}) {})]
                     (coercer (:headers request))))}]

    (let [errors (filter (comp error? second) parameters)]
      (if (not-empty errors)
        (d/error-deferred (ex-info "" {:status 400
                                       :errors errors}))
        (assoc ctx :parameters (util/remove-empty-vals parameters))))))

(defn safe-read-content-length [req]
  (let [len (get-in req [:headers "content-length"])]
    (when len
      (try
        (Long/parseLong len)
        (catch Exception e
          (throw (ex-info "Malformed Content-Length" {:value len})))))))

(defn get-properties
  [ctx]
  (let [props (get-in ctx [:resource :properties] {})
        props (if (fn? props) (props ctx) props)]
    (d/chain
     props                           ; propsfn can returned a deferred
     (fn [props]
       (let [props (ys/properties-result-coercer props)]
         (if-not (error? props)
           (cond-> ctx
             props (assoc :properties props)
             (:produces props) (assoc-in [:response :vary] (rep/vary (:produces props))))
           (d/error-deferred
            (ex-info "Properties malformed" {:status 500
                                             :error (:error props)}))))))))

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
  (assert ctx "process-request-body, ctx is nil!")

  (let [content-length (safe-read-content-length request)]
    (if (or (get-in request [:headers "transfer-encoding"])
            (and content-length (pos? content-length)))
      (let [content-type (mt/string->media-type (get-in request [:headers "content-type"]))
            content-length (safe-read-content-length request)
            consumes-mt (set (map (comp :name :media-type)
                                  (concat (get-in ctx [:resource :methods (:method ctx) :consumes])
                                          (get-in ctx [:resource :consumes]))))]

        (if-not (contains? consumes-mt (:name content-type))
          (d/error-deferred
           (ex-info "Unsupported Media Type"
                    {:status 415
                     :message "Method does not declare that it consumes this content-type"
                     :consumes consumes-mt
                     :content-type content-type}))
          (if (and content-length (pos? content-length))
            (rb/process-request-body
             ctx
             (stream/map bs/to-byte-array (bs/to-byte-buffers (:body request)))
             (:name content-type))
            ctx)))

      ;; else
      ctx)))

(defn select-representation
  "Proactively negotatiate the best representation for the payload
  body of the response. This does not mean that the
  response will have a payload body (that is determined by the
  method (which we know) and the status code (which is yet to be
  determined)."
  [ctx]
  (assert ctx "select-representation, ctx is nil!")
  (let [produces (concat (get-in ctx [:resource :methods (:method ctx) :produces])
                         (get-in ctx [:resource :produces]))
        rep (rep/select-best-representation (:request ctx) produces)]
    (cond-> ctx
      produces (assoc :produces produces)
      produces (assoc-in [:response :vary] (rep/vary produces))
      rep (assoc-in [:response :produces] rep))))

;; Conditional requests - last modified time
(defn check-modification-time [ctx]
  (if-let [last-modified (-> ctx :properties :last-modified)]

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

(defn if-match
  [ctx]
  (assert ctx "if-match, ctx is nil!")

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

          ;; Otherwise, let's use the etag we've just computed. Note,
          ;; this might yet be overridden by the (unsafe) method
          ;; returning a modified response. But if the method chooses
          ;; not to reset the etag (perhaps the resource state didn't
          ;; change), then this etag will do for the response.
          (assoc-in ctx [:response :etag]
                    (get etags (:produces ctx))))))
    ctx))

(defn if-none-match [ctx]
  (assert ctx "if-none-match, ctx is nil!")
  (if-let [matches (some->> (get-in (:request ctx) [:headers "if-none-match"]) util/parse-csv set)]

    ;; TODO: Weak comparison. Since we don't (yet) support the
    ;; issuance of weak entity tags, weak and strong comparison are
    ;; identical here.
    
    (if
        (-> ctx :properties :version)
        (let [version (-> ctx :properties :version)
              etags (into {}
                          ;; Create a map of representation -> etag. This was done in
                          ;; if-match, but it is unlikely that we have if-match and
                          ;; if-none-match in the same request, so a performance
                          ;; optimization is unwarranted.
                          (for [rep (:produces ctx)]
                            [rep (p/to-etag version rep)]))]

          (if (not-empty (set/intersection matches (set (vals etags))))
            (d/error-deferred
             (ex-info ""
                      {:status 304}))
            ctx))
        ctx)
    ctx))

(defn invoke-method
  "Methods"
  [ctx]
  (assert ctx "invoke-method, ctx is nil!")
  (assert (:method-wrapper ctx))
  (methods/request (:method-wrapper ctx) ctx))

(defn get-new-properties
  "If the method is unsafe, call properties again. This will
  pick up any changes that are used in subsequent interceptors, such as
  the new version of the resource."
  [ctx]
  (let [resource (:resource ctx)]
    (if (not (methods/safe? (:method-wrapper ctx)))

      (let [propsfn (get-in ctx [:resource :properties] (constantly {}))]
        (d/chain

         (propsfn ctx)                 ; propsfn can returned a deferred

         ;; TODO: Do validation/coercion on properties as before - see
         ;; get-properties - perhaps refactor for code re-use
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

(defn create-response
  [ctx]
  (let [method (:method ctx)
        produces (get-in ctx [:response :produces])
        body (body/to-body (get-in ctx [:response :body]) produces)

        response
        {:status (get-in ctx [:response :status] 200)
         :headers
         (merge
          (get-in ctx [:response :headers])
          ;; TODO: The context and its response
          ;; map must be documented so users are
          ;; clear what they can change and the
          ;; effect of this change.
          (when (not= (:method ctx) :options)
            (merge {}
                   (when-let [content-length
                              (let [cl (get-in ctx [:response :content-length])]
                                (cond
                                  cl (str cl)
                                  body (or (body/content-length body) (str 0))
                                  :otherwise nil))]
                     {"content-length" content-length})

                   (when-let [cookies (get-in ctx [:response :cookies])]
                     (let [cookies (cookies/cookies-coercer cookies)]
                       (if (error? cookies)
                         (warnf "Error coercing cookies: %s" (:error cookies))
                         (let [set-cookies (cookies/encode-cookies cookies)]
                           {"set-cookie" set-cookies}))))

                   (when-let [x (:media-type produces)]
                     (when (or (= method :head) body)
                       (let [y (:charset produces)]
                         (if (and y (= (:type x) "text"))
                           {"content-type" (mt/media-type->string (assoc-in x [:parameters "charset"] (charset/charset y)))}
                           {"content-type" (mt/media-type->string x)}))))
                   (when-let [x (:encoding produces)]
                     {"content-encoding" x})
                   (when-let [x (:language produces)]
                     {"content-language" x})
                   (when-let [x (get-in ctx [:response :last-modified])]
                     {"last-modified" x})
                   (when-let [x (get-in ctx [:response :vary])]
                     (when (and (not-empty x) (or (= method :head) body))
                       {"vary" (rep/to-vary-header x)}))
                   (when-let [x (get-in ctx [:response :etag])]
                     {"etag" x}))))

         :body body}]
    
    (assoc ctx :response response)))

(defn return [ctx] (:response ctx))
