;; Copyright © 2015, JUXT LTD.

(ns yada.handler
  (:require
   [clojure.tools.logging :refer [errorf debugf infof]]
   [manifold.deferred :as d]
   [schema.core :as s]
   [yada.body :as body]
   [yada.media-type :as mt]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.representation :as rep]
   [yada.response :refer [->Response]]
   [yada.schema :refer [resource-coercer]]))

(defn make-context [properties]
  {:properties properties
   :response (->Response)})

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

  p/ResourceCoercion
  (as-resource [h]
    (resource-coercer
     {:produces #{"text/html"
                  "application/edn"
                  "application/json"
                  "application/edn;pretty=true"
                  "application/json;pretty=true"}
      :methods {:get (fn [ctx] (into {} h))}})))

(defn new-handler [m]
  (map->Handler m))

