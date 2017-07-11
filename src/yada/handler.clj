;; Copyright © 2014-2017, JUXT LTD.

(ns yada.handler
  (:require
   [clojure.tools.logging :refer [errorf]]
   [manifold.deferred :as d]
   [schema.core :as s]
   [schema.utils :refer [error?]]
   [yada.body :as body]
   [yada.charset :as charset]
   [yada.context :refer [->Response]]
   [yada.methods :as methods]
   [yada.representation :as rep]
   [yada.resource :as resource :refer [as-resource resource ResourceCoercion]]
   [yada.schema :as ys :refer [resource-coercer]]
   [yada.util :refer [get*]])
  (:import clojure.lang.APersistentMap
           yada.methods.AnyMethod
           yada.resource.Resource))

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
    ;; JSON is troublesome because of its poor support for
    ;; serialization of complex types. Need a plugin mechanism now
    ;; that JSON has been demoted to an extension.
    [{:media-type #{ ;;"application/json"
                    ;;"application/json;pretty=true;q=0.96"
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
          (let [sub-resource (or (subresourcefn ctx) (resource nil))
                handler
                (new-handler
                 {:id (get resource :id (java.util.UUID/randomUUID))
                  :parent resource
                  :resource sub-resource
                  :allowed-methods (allowed-methods sub-resource)
                  :known-methods (:known-methods ctx)
                  :interceptor-chain (or
                                      (:interceptor-chain sub-resource)
                                      (-> ctx :interceptor-chain))
                  :error-interceptor-chain (or
                                            (:error-interceptor-chain sub-resource)
                                            (-> ctx :error-interceptor-chain))})]

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
                                (or (:produces custom-response) [{:media-type "text/plain"
                                                                  :charset "UTF-8"}])
                                error-representations)
                              )]

                     (apply d/chain
                            (cond-> ctx
                              e (assoc :error e)
                              ;; true (merge (select-keys ctx [:id :request :method]))
                              status (assoc-in [:response :status] status)
                              (:headers data) (assoc-in [:response :headers] (:headers data))

                              rep (assoc-in [:response :produces] rep)

                              ;; This primes the body data in case the representation is nil
                              (contains? data :body)
                              (assoc-in [:response :body] (:body data))

                              ;; This could override [:response :body]
                              (and (not (contains? data :body)) (not (:response custom-response)))
                              (standard-error status e rep)

                              ;; This could override [:response :body]
                              (and (not (contains? data :body)) (:response custom-response))
                              (custom-error (:response custom-response) rep)

                              true set-content-length)

                            (:error-interceptor-chain ctx)

                            ))))))))))

(defn handle-request
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

  ResourceCoercion
  (as-resource [h]
    (resource-coercer
     {:produces #{"text/html"
                  "application/edn"
                  "application/json"
                  "application/edn;pretty=true"
                  "application/json;pretty=true"}
      :methods {:get (fn [ctx] (into {} h))}})))

(s/defn new-handler [model :- ys/HandlerModel]
  (map->Handler model))

(defmulti interceptor-chain "" (fn [options] (:interceptor-chain options)))

(defmethod interceptor-chain :default [options]
  (if-let [ic (:interceptor-chain options)]
    (throw (ex-info (format "No default interceptor chain found for %s" ic) options))
    (throw (ex-info "No default interceptor chain defmethod defined. Have you required yada.yada (or a yada.yada sub-namespace)?" {}))))

(defmulti error-interceptor-chain "" (fn [options] (:error-interceptor-chain options)))

(defmethod error-interceptor-chain :default [options]
  (if-let [ic (:error-interceptor-chain options)]
    (throw (ex-info (format "No default error interceptor chain found for %s" ic) options))
    (throw (ex-info "No default error interceptor chain defmethod defined. Have you required yada.yada (or a yada.yada sub-namespace)?" {}))))

(defn handler
  "Create a Ring handler"
  ([resource]
   (handler resource {:interceptor-chain nil
                      :error-interceptor-chain nil}))
  ([resource options]

   (when (not (satisfies? ResourceCoercion resource))
     (throw (ex-info "The argument to the yada function must be a Resource record or something that can be coerced into one (i.e. a type that satisfies yada.protocols/ResourceCoercion)"
                     {:resource resource})))

   ;; It's possible that we're being called with a resource that already has an error
   (when (error? resource)
     (throw (ex-info "yada function is being passed a resource that is an error"
                     {:error (:error resource)})))

   (let [resource (ys/resource-coercer (as-resource resource))]

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
        :interceptor-chain (or (:interceptor-chain resource) (interceptor-chain options))
        :error-interceptor-chain (or (:error-interceptor-chain resource) (error-interceptor-chain options))})))))

;; Alias
(def yada handler)

;; We also want resources to be able to be used in bidi routes without
;; having to create yada handlers. This isn't really necessary but a
;; useful convenience to reduce verbosity.

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
  clojure.lang.Fn
  (as-handler [this] this)
  clojure.lang.Var
  (as-handler [this]
    (as-handler (deref this)))
  Object
  (as-handler [this] (handler this)))


;; Convenience functions

(defn prepend-interceptor [res & interceptors]
  (update res
          :interceptor-chain (partial into (vec interceptors))))

(defn insert-interceptor [res point & interceptors]
  (update res :interceptor-chain
          (partial mapcat (fn [i]
                            (if (= i point)
                              (concat interceptors [i])
                              [i])))))

(defn append-interceptor [res point & interceptors]
  (update res :interceptor-chain
          (partial mapcat (fn [i]
                            (if (= i point)
                              (concat [i] interceptors)
                              [i])))))
