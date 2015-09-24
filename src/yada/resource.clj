;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :as su]
   [yada.representation :as rep]
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

   (s/optional-key :collection?)
   s/Bool

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

(def coerce-properties
  (sc/coercer Properties +properties-coercions+))

(def default-properties
  {:representations
   [{:media-type #{"text/plain" "application/octet-stream;q=0.1"}
     :charset yada.charset/default-platform-charset}]})

(s/defn properties :- Properties
  [r]
  (let [res
        (coerce-properties
         (try
           (merge default-properties (p/properties r))
           (catch IllegalArgumentException e default-properties)
           (catch AbstractMethodError e default-properties)))]
    (when (su/error? res)
      (throw (ex-info "Resource properties are not valid" {:error res})))
    res))

;; ---

;; The reason we can't have multiple arities is that s/defn has a
;; limitation that 'all arities must share the same output schema'.
(s/defn properties-on-request :- Properties
  [r ctx]
  (coerce-properties
   (merge
    {:exists? true}
    (try
      (p/properties r ctx)
      (catch IllegalArgumentException e {})
      (catch AbstractMethodError e {})))))
