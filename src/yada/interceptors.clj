;; Copyright © 2014-2017, JUXT LTD.

(ns yada.interceptors
  (:require
   [byte-streams :as bs]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   ring.util.time
   [schema.utils :refer [error?]]
   [schema.core :as s]
   [yada.body :as body]
   [yada.charset :as charset]
   [yada.cookies :as cookies]
   [yada.etag :as etag]
   [yada.media-type :as mt]
   [yada.methods :as methods]
   [yada.representation :as rep]
   [yada.request-body :as rb]
   [yada.schema :as ys]
   [yada.util :as util])
  (:import java.io.ByteArrayOutputStream
           java.util.zip.GZIPInputStream))

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
                                   (map (comp (memfn ^String toUpperCase) name)
                                        (:allowed-methods ctx)))}}))))



(defn capture-proxy-headers [ctx]
  (let [req (:request ctx)

        scheme (or
                ;; As defined in RFC 7239
                (get-in req [:headers "forwarded-proto"])

                ;; Unofficial, but used by Apache and NGINX
                (get-in req [:headers "x-forwarded-proto"])

                ;; Microsoft-specific extension
                (when (= "on" (get-in req [:headers "front-end-https"]))
                  :https)

                ;; We fallback to the detected scheme, which is pretty much
                ;; always http
                (:scheme req))

        req' (assoc req :scheme (keyword scheme))]

    (assoc ctx :request req')))



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

(defn process-content-encoding
  "Handle Content-Encoding header according to RFC 2616 section 14.11."
  [{:keys [request] :as ctx}]
  (let [encoding (get-in request [:headers "content-encoding"] "identity")]
    (condp = encoding
      ;; wrap potentially expensive decompression in a future
      "gzip" (d/future
               (let [output (ByteArrayOutputStream.)]
                 (with-open [input (-> request :body GZIPInputStream.)]
                   (io/copy input output))
                 (assoc-in ctx [:request :body] (.toByteArray output))))

      "identity" ctx

      (d/error-deferred
       (ex-info "Unsupported Media Type"
                {:status 415
                 :message "The used Content-Encoding is not supported"
                 :content-encoding encoding})))))

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
          (if-let [consumer (get-in ctx [:resource :methods (:method ctx) :consumer])]
            (consumer ctx content-type
                      (:body request))

            (rb/process-request-body
             ctx
             (stream/map bs/to-byte-array (bs/to-byte-buffers (:body request)))
             (:name content-type)))))

      ;; else
      (if-let [body-schema (get-in ctx [:resource :methods (:method ctx) :parameters :body])]
        (if (s/check body-schema nil)
          (d/error-deferred
             (ex-info "No body present but body is expected for request."
                      {:status 400}))
          ctx)
        ctx))))

(defn select-representation
  "Proactively negotatiate the best representation for the payload
  body of the response. This does not mean that the
  response will have a payload body (that is determined by the
  method (which we know) and the status code (which is yet to be
  determined)."
  [ctx]
  (assert ctx "select-representation, ctx is nil!")
  (let [apply-if-fn (fn [f]
                      (if (fn? f)
                        (ys/representation-seq (ys/representation-set-coercer (f ctx)))
                        f))
        produces (concat (apply-if-fn (get-in ctx [:resource :methods (:method ctx) :produces]))
                         (apply-if-fn (get-in ctx [:resource :produces])))
        rep (rep/select-best-representation (:request ctx) produces)]
    (cond-> ctx
      produces (assoc :produces produces) ; all the possible representations
      produces (assoc-in [:response :vary] (rep/vary produces))
      rep (assoc-in [:response :produces] rep) ; the best representation
      )))

;; Conditional requests - last modified time
(defn check-modification-time [ctx]
  (if-let [^java.util.Date last-modified (-> ctx :properties :last-modified)]

    (if-let [^java.util.Date if-modified-since
             (some-> (:request ctx)
                     (get-in [:headers "if-modified-since"])
                     ring.util.time/parse-date)]

      (if (<=
           (.getTime last-modified)
           (.getTime if-modified-since))

        (assoc-in ctx [:response :status] 304)
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
                          [rep (etag/to-etag version rep)]))]

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
                            [rep (etag/to-etag version rep)]))]

          (if (not-empty (set/intersection matches (set (vals etags))))
            (assoc-in ctx [:response :status] 304)
            ctx))
        ctx)
    ctx))

(defn invoke-method
  "Methods"
  [ctx]
  (assert ctx "invoke-method, ctx is nil!")
  (assert (:method-wrapper ctx))
  ;; We check for a 304. A 304 does not escape through an error
  ;; mechanism because RFC 7232 still demands we calculate the ETag,
  ;; Vary and other headers.
  (if (= (get-in ctx [:response :status]) 304)
    ctx
    (methods/request (:method-wrapper ctx) ctx)))

(defn get-new-properties
  "If the method is unsafe, call properties again. This will
  pick up any changes that are used in subsequent interceptors, such as
  the new version of the resource."
  [ctx]
  (cond
    ;; If it's a 304, we know that no properties can have changed
    ;; since no method has been invoked.
    (= (get-in ctx [:response :status]) 304)
    ctx

    (not (methods/safe? (:method-wrapper ctx)))
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
            (:produces props)    ; representations shorthand is
                                        ; expanded, is this necessary at this
                                        ; stage?
            (update-in [:produces]
                       (comp ys/representation-seq ys/representation-set-coercer)))))))
    :otherwise ctx))

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
    (let [etag (etag/to-etag version (get-in ctx [:response :produces]))]
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
          (when (not= (:method ctx) :options)
            (merge {}
                   (when (not= (:method ctx) :head)
                     (when-let [cl (body/content-length body)]
                       {"content-length" (str cl)}))

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
                   (when-let [x (-> produces :language :language)]
                     {"content-language" (apply str (interpose "-" x))})
                   (when-let [x (get-in ctx [:response :last-modified])]
                     {"last-modified" x})
                   (when-let [x (get-in ctx [:response :vary])]
                     (when (not-empty x)
                       {"vary" (rep/to-vary-header x)}))
                   (when-let [x (get-in ctx [:response :etag])]
                     {"etag" x}))))

         :body body}]

    (assoc ctx :response response)))

(defn logging [ctx]
  (or
   (when-let [logger (-> ctx :resource :logger)]
     ;; Loggers can return a modified context, affecting the response
     (logger ctx))
   ;; If no logger, or a logger returns nil, return the original
   ;; context
   ctx))


(defn return [ctx] (:response ctx))
