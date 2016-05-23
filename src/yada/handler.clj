;; Copyright © 2015, JUXT LTD.

(ns yada.handler
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring :as br]
   [bidi.vhosts :as bv]
   [clojure.tools.logging :refer [errorf debugf infof]]
   [manifold.deferred :as d]
   [schema.core :as s]
   [schema.utils :refer [error?]]
   [yada.body :as body]
   [clojure.pprint :refer [pprint]]
   [yada.context :refer [->Response]]
   [yada.charset :as charset]
   [yada.interceptors :as i]
   [yada.media-type :as mt]
   [yada.methods :as methods]
   [yada.protocols :as p]
   [yada.representation :as rep]
   [yada.resource :as resource :refer [resource]]
   [yada.schema :refer [resource-coercer] :as ys]
   [yada.security :as sec]
   [yada.util :refer [get*]])
  (:import
   [bidi.vhosts VHostsModel]
   [clojure.lang PersistentVector APersistentMap]
   [yada.resource Resource]
   [yada.methods AnyMethod]))

(declare new-handler)

(defn make-context []
  {:response (assoc (->Response) :headers {})})

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

;; Response

(defn allowed-methods [resource]
  (let [methods (set (keys (:methods resource)))]
    (cond-> methods
      (some #{:get} methods) (conj :head)
      true (conj :options))))

;; Possibly in future it will be possible
;; to support more media-types to render
;; errors, including image and video
;; formats.
(def error-representations
  (ys/representation-seq
   (ys/representation-set-coercer
    [{:media-type #{"application/json"
                    "application/json;pretty=true;q=0.96"
                    "text/plain;q=0.9"
                    "text/html;q=0.8"
                    "application/edn;q=0.6"
                    "application/edn;pretty=true;q=0.5"}
      :charset charset/platform-charsets}])))

(defn standard-error [ctx status e rep]
  (let [errbody (body/to-body (body/render-error status e rep ctx) rep)]
    (assoc-in ctx [:response :body] errbody)))

(defn custom-error [ctx response rep]
  (let [err (response ctx)
        ctx (methods/interpret-get-result err ctx)]
    (update-in ctx [:response :body] body/to-body rep)))

(defn set-content-length [ctx]
  (assoc-in ctx [:response :headers "content-length"] (str (body/content-length (get-in ctx [:response :body])))))

(defn- handle-request-with-maybe-subresources [ctx]
  (let [resource (:resource ctx)
        error-handler default-error-handler]

    (if
        ;; If the resource provies sub-resources, call its sub-resource
        ;; function.  However, if the resource declares it requires
        ;; path-info, only call this if path-info exists, otherwise
        ;; call the parent resource as normal.
        (and (:sub-resource resource)
             (or (:path-info (:request ctx))
                 (not (:path-info? resource))))

      (let [subresourcefn (:sub-resource resource)]
        ;; Subresource
        (let [sub-resource (subresourcefn ctx)
              handler
              (new-handler
               {:id (get resource :id (java.util.UUID/randomUUID))
                :parent resource
                :resource sub-resource
                :allowed-methods (allowed-methods sub-resource)
                :known-methods (:known-methods ctx)
                :interceptor-chain (or
                                    (:interceptor-chain sub-resource)
                                    (-> ctx :interceptor-chain))})]

          (handle-request-with-maybe-subresources
           (-> ctx
               (merge handler)))))

      ;; Normal resources
      (->
       (apply d/chain ctx (:interceptor-chain ctx))

       (d/catch
           java.lang.Exception
           (fn [e]
             (error-handler e)
             (let [data (error-data e)]
               (let [status (or (:status data) 500)]

                 (let [custom-response (get* (:responses resource) status)
                       rep (rep/select-best-representation
                            (:request ctx)
                            (if custom-response
                              (or (:produces custom-response) "text/plain")
                              error-representations)
                            )]

                   (d/chain
                    (cond-> ctx
                      e (assoc :error e)
                      ;; true (merge (select-keys ctx [:id :request :method]))
                      status (assoc-in [:response :status] status)
                      (:headers data) (assoc-in [:response :headers] (:headers data))

                      rep (assoc-in [:response :produces] rep)

                      (:body data)
                      (assoc [:response :body] (:body data))

                      (and (not (:body data)) (not (:response custom-response)))
                      (standard-error status e rep)

                      (and (not (:body data)) (:response custom-response))
                      (custom-error (:response custom-response) rep)

                      true set-content-length)

                    sec/access-control-headers
                    i/create-response
                    i/logging
                    i/return))))))))))

(defn- handle-request
  "Handle Ring request"
  [handler request match-context]
  (let [method (:request-method request)
        method-wrapper (or (get (:known-methods handler) method)
                           (when (-> handler :resource :methods :*) (new AnyMethod)))
        id (java.util.UUID/randomUUID)]
    (handle-request-with-maybe-subresources
     ;; TODO: Possibly we should merge the request-specific details
     ;; with the handler, and remove the explicit distinction between
     ;; the handler and request.

     ;; TODO: Possibly no real need for the convenient
     ;; method-wrapper. Perhaps better to use yada.context to access
     ;; this structure.
     (merge (make-context)
            match-context
            handler
            {:request-id id
             :request request
             :method method
             :method-wrapper method-wrapper}))))

(defrecord Handler []
  clojure.lang.IFn
  (invoke [this req]
    (handle-request this req (make-context)))

  p/ResourceCoercion
  (as-resource [h]
    (resource-coercer
     {:produces #{"text/html"
                  "application/edn"
                  "application/json"
                  "application/edn;pretty=true"
                  "application/json;pretty=true"}
      :methods {:get (fn [ctx] (into {} h))}}))

  bidi/Matched
  (resolve-handler [this m]
    ;; If we represent a collection of resources, let's match and retain
    ;; the remainder which we place into the request as :path-info (see
    ;; below).
    (if (-> this :resource :path-info?)
      (assoc m :handler this)
      (bidi/succeed this m)))

  (unresolve-handler [this m]
    (when
        (or (= this (:handler m))
            (when-let [id (:id this)] (= id (:handler m))))
        ""))

  bidi/RouteSeq
  (gather [this context]
    [(bidi/map->Route
      (merge
       (assoc context :handler this)
       (when-let [id (some-> this :resource :id)] {:tag id})))])

  br/Ring
  (request [this req match-context]
    (handle-request
     this
     (if (and (-> this :resource :path-info?)
              (not-empty (:remainder match-context)))
       (assoc req :path-info (:remainder match-context))
       req)
     match-context)))

(s/defn new-handler [model :- ys/HandlerModel]
  (map->Handler model))


(def default-interceptor-chain
  [i/available?
   i/known-method?
   i/uri-too-long?
   i/TRACE
   i/method-allowed?
   i/parse-parameters
   sec/authenticate ; step 1
   i/get-properties ; step 2
   sec/authorize ; steps 3,4 and 5
   i/process-request-body
   i/check-modification-time
   i/select-representation
   ;; if-match and if-none-match computes the etag of the selected
   ;; representations, so needs to be run after select-representation
   ;; - TODO: Specify dependencies as metadata so we can validate any
   ;; given interceptor chain
   i/if-match
   i/if-none-match
   i/invoke-method
   i/get-new-properties
   i/compute-etag
   sec/access-control-headers
   #_sec/security-headers
   i/create-response
   i/logging
   i/return
   ])

(defn handler
  "Create a Ring handler"
  ([resource]

   (when (not (satisfies? p/ResourceCoercion resource))
     (throw (ex-info "The argument to the yada function must be a Resource record or something that can be coerced into one (i.e. a type that satisfies yada.protocols/ResourceCoercion)"
                     {:resource resource})))

   ;; It's possible that we're being called with a resource that already has an error
   (when (error? resource)
     (throw (ex-info "yada function is being passed a resource that is an error"
                     {:error (:error resource)})))

   (let [resource (ys/resource-coercer (p/as-resource resource))]

     (when (error? resource)
       (throw (ex-info "Resource does not conform to schema"
                       {:resource resource
                        :error (:error resource)
                        :schema ys/Resource})))

     (new-handler
      (merge
       {:id (get resource :id (java.util.UUID/randomUUID))
        :resource resource
        :allowed-methods (allowed-methods resource)
        :known-methods (methods/known-methods)
        :interceptor-chain (or (:interceptor-chain resource)
                               default-interceptor-chain)})))))

;; Alias
(def yada handler)

;; We also want resources to be able to be used in bidi routes without
;; having to create yada handlers. This isn't really necessary but a
;; useful convenience to reduce verbosity.

(extend-type Resource
  bidi/Matched
  (resolve-handler [resource m]
    (if (:path-info? resource)
      (assoc m :handler resource)
      (bidi/succeed resource m)))

  (unresolve-handler [resource m]
    (when
        (or (= resource (:handler m))
            (when-let [id (:id resource)] (= id (:handler m))))
        ""))

  bidi/RouteSeq
  (gather [this context]
    [(bidi/map->Route
      (merge
       (assoc context :handler this)
       (when-let [id (:id this)] {:tag id})))])

  br/Ring
  (request [resource req match-context]
    (br/request (new-handler
                 (merge
                  {:id (get resource :id (java.util.UUID/randomUUID))
                   :resource resource
                   :allowed-methods (allowed-methods resource)
                   :known-methods (methods/known-methods)
                   :interceptor-chain (or (:interceptor-chain resource) default-interceptor-chain)}))
                req match-context)))


;; Coercions

(defprotocol HandlerCoercion
  (as-handler [_] "Coerce to a handler"))

(extend-protocol HandlerCoercion
  Handler
  (as-handler [this] this)
  Resource
  (as-handler [this] (handler this))
  APersistentMap
  (as-handler [route] (as-handler (resource route)))
  PersistentVector
  (as-handler [route] (br/make-handler route))
  VHostsModel
  (as-handler [model] (bv/make-handler model))
  Object
  (as-handler [this] (handler this)))
