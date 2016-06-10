;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require
   [clojure.tools.logging :refer :all]
   [hiccup.core :refer [html]]
   [manifold.deferred :as d]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :as su]
   [yada.representation :as rep]
   [yada.interceptors :as i]
   [yada.security :as sec]
   [yada.schema :as ys]
   [yada.context :refer [content-type]]
   yada.charset
   yada.media-type
   [yada.protocols :as p]
   [yada.util :refer [arity]])
  (:import [yada.charset CharsetMap]
           [yada.media_type MediaTypeMap]
           [java.util Date]))

;; Deprecated

(s/defschema MediaTypeSchema
  (s/either String MediaTypeMap))

(s/defschema CharsetSchema
  (s/either String CharsetMap))

(s/defschema QualifiedKeyword
  (s/both s/Keyword (s/pred namespace)))

(s/defschema MediaTypeSchemaSet
  #{MediaTypeSchema})

(s/defschema CharsetSchemaSet
  #{CharsetSchema})

(s/defschema StringSet
  #{String})

(defn as-set [x] (if (coll? x) x (set [x])))

(def +properties-coercions+
  {Date #(condp instance? %
           java.lang.Long (Date. %)
           %)
   MediaTypeSchemaSet as-set
   CharsetSchemaSet as-set
   StringSet as-set})

;; --

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

(def default-error-interceptor-chain
  [sec/access-control-headers
   i/create-response
   i/logging
   i/return])

;; --

(defrecord Resource []
  p/ResourceCoercion
  (as-resource [this] this))

(defn resource [model]
  (let [r (ys/resource-coercer
           (merge
            {:interceptor-chain default-interceptor-chain
             :error-interceptor-chain default-error-interceptor-chain}
            model))]
    (when (su/error? r) (throw (ex-info "Cannot turn resource-model into resource, because it doesn't conform to a resource-model schema" {:resource-model model :error (:error r)})))
    (map->Resource r)))

(extend-protocol p/ResourceCoercion
  nil
  (as-resource [_]
    (resource
     {:summary "Nil resource"
      :methods {}
      :interceptor-chain [(fn [_]
                            (throw (ex-info "" {:status 404})))]}))
  clojure.lang.Fn
  (as-resource [f]
    (let [ar (arity f)]
      (resource
       {:produces #{"text/html"
                    "text/plain"
                    "application/json"
                    "application/edn"}
        :methods
        {:* {:response (fn [ctx]
                         (case ar
                           0 (f)
                           1 (f ctx)
                           (apply f ctx (repeat (dec arity) nil)))
                         )}}})))

  Exception
  (as-resource [e]
    (resource
     {:produces #{"text/html" "text/plain;q=0.9"}
      :methods
      {:* {:response
           (fn [ctx]
             (case (content-type ctx)
               "text/html"
               (html
                [:body
                 (interpose [:p "Caused by"]
                            (for [e (take-while some? (iterate (fn [x] (.getCause x)) e))]
                              [:div
                               [:h2 "Error: " (.getMessage e)]
                               [:div
                                (for [stl (.getStackTrace e)]
                                  [:p [:tt stl]])]]))])))}}})))



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
