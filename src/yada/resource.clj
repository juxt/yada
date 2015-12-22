;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :as su]
   [yada.representation :as rep]
   [yada.schema :as ys]
   yada.charset
   yada.media-type
   [yada.protocols :as p])
  (:import [yada.charset CharsetMap]
           [yada.media_type MediaTypeMap]
           [java.util Date]))

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

(s/defschema RepresentationSet
  {(s/optional-key :media-type) MediaTypeSchemaSet
   (s/optional-key :charset) CharsetSchemaSet
   (s/optional-key :encoding) StringSet
   (s/optional-key :language) StringSet})

(s/defschema RepresentationSets
  [RepresentationSet])

;; Final representation
(s/defschema Representation
  {(s/optional-key :media-type) MediaTypeMap
   (s/optional-key :charset) CharsetMap
   (s/optional-key :encoding) String
   (s/optional-key :language) String})

(s/defschema Properties
  {(s/optional-key :allowed-methods)
   (s/either [s/Keyword] #{s/Keyword})

   (s/optional-key :parameters)
   {s/Keyword ;; method
    {(s/optional-key :query) {s/Any s/Any}
     (s/optional-key :path) {s/Any s/Any}
     (s/optional-key :header) {s/Any s/Any}
     (s/optional-key :form) {s/Any s/Any}
     (s/optional-key :body) s/Any}}

   (s/optional-key :representations)
   RepresentationSets

   (s/optional-key :last-modified) Date
   (s/optional-key :version) s/Any
   (s/optional-key :collection?) s/Bool
   (s/optional-key :exists?) s/Bool

   QualifiedKeyword s/Any})

(defn as-set [x] (if (coll? x) x (set [x])))

(def +properties-coercions+
  {Date #(condp instance? %
           java.lang.Long (Date. %)
           %)
   MediaTypeSchemaSet as-set
   CharsetSchemaSet as-set
   StringSet as-set})


;; --
(defrecord Resource []
  p/ResourceCoercion
  (as-resource [this] this))

(defn resource [m]
  (let [r (ys/resource-coercer m)]
    (when (su/error? r) (throw (ex-info "Cannot turn map into resource, because it doesn't conform to a resource schema" {:input-map m :error (:error r)})))
    (map->Resource r)))

(extend-protocol p/ResourceCoercion
  nil
  (as-resource [_] (resource {:properties {:exists? false}
                              :methods {:get nil}})))

