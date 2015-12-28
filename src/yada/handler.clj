;; Copyright © 2015, JUXT LTD.

(ns yada.handler
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring :as br]
   [clojure.tools.logging :refer [errorf debugf infof]]
   [manifold.deferred :as d]
   [schema.core :as s]
   [yada.body :as body]
   [clojure.pprint :refer [pprint]]
   [yada.media-type :as mt]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.representation :as rep]
   [yada.response :refer [->Response]]
   [yada.resource :as resource]
   [yada.schema :refer [resource-coercer] :as ys]))

(declare new-handler)

(defn make-context []
  {:response (->Response)})

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
(defn create-response
  [ctx]

  (let [method (:method ctx)
        body (get-in ctx [:response :body])

        response
        {:status (get-in ctx [:response :status] 200)
         :headers (merge
                   (get-in ctx [:response :headers])
                   ;; TODO: The context and its response
                   ;; map must be documented so users are
                   ;; clear what they can change and the
                   ;; effect of this change.
                   (when (not= (:method ctx) :options)
                     (merge {}
                            (when-let [x (get-in ctx [:response :produces :media-type])]
                              (when (or (= method :head) body)
                                (let [y (get-in ctx [:response :produces :charset])]
                                  (if (and y (= (:type x) "text"))
                                    {"content-type" (mt/media-type->string (assoc-in x [:parameters "charset"] (charset/charset y)))}
                                    {"content-type" (mt/media-type->string x)}))))
                            (when-let [x (get-in ctx [:response :produces :encoding])]
                              {"content-encoding" x})
                            (when-let [x (get-in ctx [:response :produces :language])]
                              {"content-language" x})
                            (when-let [x (get-in ctx [:response :last-modified])]
                              {"last-modified" x})
                            (when-let [x (get-in ctx [:response :vary])]
                              (when (and (not-empty x) (or (= method :head) body))
                                {"vary" (rep/to-vary-header x)}))
                            (when-let [x (get-in ctx [:response :etag])]
                              {"etag" x})))
                   (when-let [x (get-in ctx [:response :content-length])]
                     {"content-length" x}))

         ;; TODO :status and :headers should be implemented like this in all cases
         :body (get-in ctx [:response :body])}]
    (debugf "Returning response: %s" (dissoc response :body))
    response))

(defn allowed-methods [resource]
  (let [methods (set (keys (:methods resource)))]
    (cond-> methods
      (some #{:get} methods) (conj :head)
      true (conj :options))))

(defn- handle-request-with-maybe-subresources [ctx]
  (let [resource (-> ctx :handler :resource)
        error-handler default-error-handler]

    (if
        ;; If the resource provies subresources, call its subresource
        ;; function.  However, if the resource declares it requires
        ;; path-info, only call this if path-info exists, otherwise
        ;; call the parent resource as normal.
        (and (:subresource resource)
             (or (:path-info (:request ctx))
                 (not (:path-info? resource))))

        (let [subresourcefn (:subresource resource)]
          ;; Subresource
          (let [subresource (subresourcefn ctx)
                handler
                (new-handler
                 {:id (get resource :id (java.util.UUID/randomUUID))
                  :parent resource
                  :resource subresource
                  :allowed-methods (allowed-methods subresource)
                  :known-methods (-> ctx :handler :known-methods)
                  ;; TODO: Could/should subresources, which are dynamic, be able
                  ;; to modify the interceptor-chain?
                  :interceptor-chain (-> ctx :handler :interceptor-chain)})]
          
            (handle-request-with-maybe-subresources
             (-> ctx
                 (dissoc :base)
                 (assoc :allowed-methods (allowed-methods subresource)
                        :handler handler)))))

        ;; Normal resources
        (->
         (apply d/chain ctx (-> ctx :handler :interceptor-chain))

         (d/catch
             clojure.lang.ExceptionInfo
             (fn [e]
               (error-handler e)
               (let [data (error-data e)]
                 (let [status (or (:status data) 500)
                       rep (rep/select-best-representation
                            (:request ctx)
                            ;; TODO: Don't do this! coerce!!
                            (rep/representation-seq
                             (rep/coerce-representations
                              ;; Possibly in future it will be possible
                              ;; to support more media-types to render
                              ;; errors, including image and video
                              ;; formats.

                              [{:media-type #{"application/json"
                                              "application/json;pretty=true;q=0.96"
                                              "text/plain;q=0.9"
                                              "text/html;q=0.8"
                                              "application/edn;q=0.6"
                                              "application/edn;pretty=true;q=0.5"}
                                :charset charset/platform-charsets}])))]

                   ;; TODO: Custom error handlers

                   (d/chain
                    (cond-> (make-context)
                      status (assoc-in [:response :status] status)
                      (:headers data) (assoc-in [:response :headers] (:headers data))
                      (not (:body data)) ((fn [ctx]
                                            (let [b (body/to-body (body/render-error status e rep ctx) rep)]
                                              (-> ctx
                                                  (assoc-in [:response :body] b)
                                                  (assoc-in [:response :headers "content-length"] (body/content-length b))))))

                      rep (assoc-in [:response :produces] rep))
                    create-response)))))))))

(defn- handle-request
  "Handle Ring request"
  [handler request match-context]
  (let [method (:request-method request)]
    (handle-request-with-maybe-subresources
     (merge (make-context)
            {:id (java.util.UUID/randomUUID)
             :request request
             :method method
             :method-wrapper (get (:known-methods handler) method)
             :handler handler
             }))))

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
    (if (:path-info? this)
      (assoc m :handler this)
      (bidi/succeed this m)))

  (unresolve-handler [this m]
    (when
        (or (= this (:handler m))
            (when-let [id (:id this)] (= id (:handler m))))
        ""))

  br/Ring
  (request [this req match-context]
    (handle-request
     this
     (if (and (:path-info? this)
              (not-empty (:remainder match-context)))
         (assoc req :path-info (:remainder match-context))
       req)
     (merge (make-context) match-context))))

(s/defn new-handler [model :- ys/HandlerModel]
  (map->Handler model))

