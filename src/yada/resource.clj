;; Copyright © 2014-2017, JUXT LTD.

(ns yada.resource
  (:require
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [schema.utils :as su]
   [yada.schema :as ys]
   [yada.context :refer [content-type]]
   [yada.util :refer [arity]])
  (:import [yada.charset CharsetMap]
           [yada.media_type MediaTypeMap]
           [java.util Date]))

(defprotocol ResourceCoercion
  (as-resource [_] "Coerce to a resource. Often, resources need to be
  coerced rather than extending types directly with the Resource
  protocol. We can exploit the time of coercion to know the time of
  birth for the resource, which supports time-based conditional
  requests. For example, a simple StringResource is immutable, so by
  knowing the time of construction, we can precisely state its
  Last-Modified-Date."))

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

(defrecord Resource []
  ResourceCoercion
  (as-resource [this] this))

(defn resource [model]
  (let [r (ys/resource-coercer model)]
    (when (su/error? r) (throw (ex-info "Cannot turn resource-model into resource, because it doesn't conform to a resource-model schema" {:resource-model model :error (:error r)})))
    (map->Resource r)))

(extend-protocol ResourceCoercion
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
