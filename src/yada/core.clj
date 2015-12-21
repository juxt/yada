;; Copyright © 2015, JUXT LTD.

(ns yada.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [byte-streams :as bs]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   [ring.middleware.params :refer [assoc-query-params]]
   [ring.swagger.schema :as rs]
   [ring.swagger.coerce :as rsc]
   [schema.coerce :as sc]
   [schema.utils :refer [error?]]
   [yada.coerce :as coerce]
   [yada.handler :refer [new-handler create-response]]
   [yada.methods :as methods]
   [yada.media-type :as mt]
   [yada.representation :as rep]
   [yada.protocols :as p]
   [yada.request-body :as rb]
   [yada.multipart]
   [yada.schema :as ys]
   [yada.util :as util])
  (:import [java.util Date]))

#_(defn available?
  "Is the service available?"
  [ctx]
  (let [res (service/service-available? (-> ctx :handler :service-available?) ctx)]
    (if-not (service/interpret-service-available res)
      (d/error-deferred
       (ex-info "" (merge {:status 503}
                          (when-let [retry-after (service/retry-after res)] {:headers {"retry-after" retry-after}}))))
      ctx)))

(defn exists?
  "Does this resource exist? Can we tell without calling properties?
  For example, this is to allow resources to declare they don't
  actually exist, e.g. the nil resource, by providing static
  properties. The value of the exists?  entry must be explicitly false
  not just falsey (nil)."
  [ctx]
  (if (false? (get-in ctx [:handler :resource :properties :exists?]))
    (d/error-deferred (ex-info "" {:status 404}))
    ctx))

(defn known-method?
  [ctx]
  (if-not (:method-wrapper ctx)
    (d/error-deferred (ex-info "" {:status 501 ::method (:method ctx)}))
    ctx))

#_(defn uri-too-long?
  [ctx]
  (if (service/request-uri-too-long? (-> ctx :request-uri-too-long?)
                                     (-> ctx :request :uri))
    (d/error-deferred (ex-info "" {:status 414}))
    ctx))

(defn TRACE
  [ctx]
  (if (#{:trace} (:method ctx))
    (methods/request (:method-wrapper ctx) ctx)
    ctx))

(defn method-allowed?
  "Is method allowed on this resource?"
  [ctx]
  (if-not (contains? (-> ctx :handler :allowed-methods) (:method ctx))
    (d/error-deferred
     (ex-info "Method Not Allowed"
              {:status 405
               :headers {"allow"
                         (str/join ", "
                                   (map (comp (memfn toUpperCase) name)
                                        (-> ctx :handler :allowed-methods)))}}))
    ctx))

(defn parse-parameters
  "Parse request and coerce parameters."
  [ctx]
  (let [method (:method ctx)
        request (:request ctx)

        schemas (util/merge-parameters (get-in ctx [:handler :parameters])
                                       (get-in ctx [:handler :methods method :parameters]))

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
                    
         #_:header #_(when-let [schema (get-in parameters [method :header])]
                       (let [params (-> request :headers)]
                         (rs/coerce (assoc schema String String) params :query)))}]

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
  (let [props (get-in ctx [:handler :properties] {})
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
  (let [content-length (safe-read-content-length request)]
    (if (or (get-in request [:headers "transfer-encoding"])
            (and content-length (pos? content-length)))
      (let [content-type (mt/string->media-type (get-in request [:headers "content-type"]))
            content-length (safe-read-content-length request)
            consumes-mt (set (map (comp :name :media-type)
                                  (or (get-in ctx [:properties :consumes])
                                      (concat (get-in ctx [:handler :methods (:method ctx) :consumes])
                                              (get-in ctx [:handler :consumes])))))]

        (if-not (contains? consumes-mt (:name content-type))
          (d/error-deferred
           (ex-info "Unsupported Media Type"
                    {:status 415
                     :message "Method does not declare that it consumes this content-type"
                     :consumes consumes-mt
                     :content-type content-type}))
          (if (and content-length (pos? content-length))
            (rb/process-request-body ctx (stream/map bs/to-byte-array (bs/to-byte-buffers (:body request))) (:name content-type))
            ctx)))

      ;; else
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

(def realms-xf
  (comp
   (filter (comp (partial = :basic) :type))
   (map :realm)))

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
  "Proactively negotatiate the best representation for the payload
  body of the response. This does not mean that the
  response will have a payload body (that is determined by the
  method (which we know) and the status code (which is yet to be
  determined)."
  [ctx]
  ;; TODO: Need metadata to say whether the :produces property 'replaces'
  ;; or 'augments' the static produces declaration. Currently only
  ;; 'replaces' is supported. What does Swagger do?
  
  ;; TODO We select the representation

  (let [produces (or (get-in ctx [:properties :produces])
                     (concat (get-in ctx [:handler :methods (:method ctx) :produces])
                             (get-in ctx [:handler :produces])))
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

          (when (not-empty (set/intersection matches (set (vals etags))))
            (d/error-deferred
             (ex-info ""
                      {:status 304})))))
    ctx))

(defn invoke-method
  "Methods"
  [ctx]
  (methods/request (:method-wrapper ctx) ctx))

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

(defn access-control-headers [ctx]
  (let [access-control (get-in ctx [:handler :access-control]) 
        ;;interpreted-allow-origin (when allow-origin (service/allow-origin allow-origin ctx))
        expose-headers (get-in ctx [:access-control :expose-headers])
        allow-headers (get-in ctx [:access-control :allow-headers])]

    (cond-> ctx
      (:allow-origin access-control)
      (assoc-in [:response :headers "access-control-allow-origin"] (:allow-origin access-control))

      (:expose-headers access-control)
      (assoc-in [:response :headers "access-control-expose-headers"]
                (apply str
                       (interpose ", " (:expose-headers access-control))))

      (:allow-headers access-control)
      (assoc-in [:response :headers "access-control-allow-headers"]
                (apply str
                       (interpose ", " (:allow-headers access-control)))))))

(def default-interceptor-chain
  [;;available?
   exists? 
   known-method?
   ;;uri-too-long?
   TRACE
   method-allowed?
   parse-parameters
   get-properties
   process-request-body
;;   authentication
;;   authorization
   check-modification-time
   select-representation
   ;; if-match and if-none-match computes the etag of the selected
   ;; representations, so needs to be run after select-representation
   ;; - TODO: Specify dependencies as metadata so we can validate any
   ;; given interceptor chain
   if-match
   if-none-match
   invoke-method
   get-new-properties
   compute-etag
   access-control-headers
   create-response])

(defn yada
  "Create a Ring handler"
  ([resource]

   (when (not (satisfies? p/ResourceCoercion resource))
     (throw (ex-info "Resource must satisfy ResourceCoercion" {:resource resource})))
   
   ;; It's possible that we're being called with a resource that already has an error
   (when (error? resource)
     (throw (ex-info "yada function is being passed a resource that is an error"
                     {:error (:error resource)})))

   (let [base resource

         ;; Validate the resource structure, with coercion if
         ;; necessary.
         resource (ys/resource-coercer (p/as-resource resource))

         _ (when (error? resource)
             (throw (ex-info "Resource does not conform to schema"
                             {:resource (p/as-resource base)
                              :error (:error resource)
                              :schema ys/Resource})))

         ;; This handler services a collection of resources
         ;; (TODO: this is ambiguous, what do we mean exactly?)
         ;; See yada.resources.file-resource for an example.
         collection? (:collection? resource)

         known-methods (methods/known-methods)


         allowed-methods (let [methods (set (keys (:methods resource)))]
                           (cond-> methods
                             (some #{:get} methods) (conj :head)
                             true (conj :options)))]

     (new-handler
      (merge {:id (java.util.UUID/randomUUID)
              :base base
              :resource resource
              :allowed-methods allowed-methods
              :known-methods known-methods
              :interceptor-chain default-interceptor-chain
              :collection? collection?}
             resource)))))
