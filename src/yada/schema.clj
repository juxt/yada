;; Copyright © 2015, JUXT LTD.

(ns ^{:doc
      "This namespace provides the coercions to transform a wide
variety of shorthand descriptions of a resource into a canonical
resource map. This allows the rest of the yada code-base to remain
agnostic to the syntax of shorthand forms, which significantly
simplifies coding while giving the author of yada resources the
convenience of terse, expressive short-hand descriptions."}
    yada.schema
  (:require
   [clojure.walk :refer [postwalk]]
   [yada.media-type :as mt]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :refer [error?]]
   [yada.charset :refer [to-charset-map]])
  (:import
   [yada.charset CharsetMap]
   [yada.media_type MediaTypeMap]))

(s/defschema NamespacedKeyword
  (s/constrained s/Keyword namespace))

(defprotocol SetCoercion
  (as-set [_] ""))

(extend-protocol SetCoercion
  clojure.lang.APersistentSet
  (as-set [s] s)
  Object
  (as-set [s] #{s}))

(defprotocol VectorCoercion
  (as-vector [_] ""))

(extend-protocol VectorCoercion
  clojure.lang.PersistentVector
  (as-vector [v] v)
  Object
  (as-vector [o] [o]))

(s/defschema ResourceParameters
  {(s/optional-key :parameters)
   {(s/optional-key :query) s/Any
    (s/optional-key :path) s/Any
    (s/optional-key :cookie) s/Any
    (s/optional-key :header) s/Any
    }})

(def ParametersMappings {})

(s/defschema Representation
  (s/constrained
   {:media-type MediaTypeMap
    (s/optional-key :charset) CharsetMap
    (s/optional-key :language) String
    (s/optional-key :encoding) String}
   not-empty))

(s/defschema RepresentationSet
  (s/constrained
   {:media-type #{MediaTypeMap}
    (s/optional-key :charset) #{CharsetMap}
    (s/optional-key :language) #{String}
    (s/optional-key :encoding) #{String}}
   not-empty))

(defn representation-seq
  "Return a sequence of all possible individual representations from the
  result of coerce-representations."
  [reps]
  (for [rep reps
        media-type (or (:media-type rep) [nil])
        charset (or (:charset rep) [nil])
        language (or (:language rep) [nil])
        encoding (or (:encoding rep) [nil])]
    (merge
     (when media-type {:media-type media-type})
     (when charset {:charset charset})
     (when language {:language language})
     (when encoding {:encoding encoding}))))

(defprotocol MediaTypeCoercion
  (as-media-type [_] ""))

(defprotocol RepresentationSetCoercion
  (as-representation-set [_] ""))

(extend-protocol MediaTypeCoercion
  MediaTypeMap
  (as-media-type [mt] mt)
  String
  (as-media-type [s] (mt/string->media-type s)))

(extend-protocol RepresentationSetCoercion
  clojure.lang.APersistentSet
  (as-representation-set [s] {:media-type s})
  clojure.lang.APersistentMap
  (as-representation-set [m] m)
  String
  (as-representation-set [s] {:media-type s}))

(def RepresentationSetMappings
  {[RepresentationSet] as-vector
   RepresentationSet as-representation-set
   #{MediaTypeMap} as-set
   MediaTypeMap as-media-type
   #{CharsetMap} as-set
   CharsetMap to-charset-map})

(def representation-set-coercer
  (sc/coercer [RepresentationSet] RepresentationSetMappings))

(def RepresentationSeqMappings
  ;; If representation-set-coercer is an error, don't proceed with the
  ;; representation-set-coercer
  {[Representation] (fn [x]
                      (let [s (representation-set-coercer x)]
                        (if (error? s)
                          s
                          (representation-seq s))))})

(def representation-seq-coercer
  (sc/coercer [Representation] RepresentationSeqMappings))

(s/defschema Produces
  {(s/optional-key :produces) [Representation]})

(s/defschema Consumes
  {(s/optional-key :consumes) [Representation]})

(defprotocol FunctionCoercion
  (as-fn [_] "Coerce to function"))

(extend-protocol FunctionCoercion
  clojure.lang.Fn
  (as-fn [f] f)
  Object
  (as-fn [o] (constantly o))
  nil
  (as-fn [_] (constantly nil)))

(s/defschema Context {})

(s/defschema HandlerFunction
  (s/=> s/Any Context))

(s/defschema Handler
  {:handler HandlerFunction})

(s/defschema StaticProperties
  {(s/optional-key :last-modified) s/Inst
   (s/optional-key :version) s/Any
   (s/optional-key :exists?) s/Bool
   NamespacedKeyword s/Any})

(s/defschema PropertiesResult
  (merge StaticProperties
         Produces))

(s/defschema PropertiesHandlerFunction
  (s/=> PropertiesResult Context))

(s/defschema Properties
  {(s/optional-key :properties) (s/conditional
                                 fn? PropertiesHandlerFunction
                                 (comp not fn?) StaticProperties)})

(def PropertiesMappings {})

(def PropertiesResultMappings (merge RepresentationSeqMappings))

(def properties-result-coercer (sc/coercer PropertiesResult PropertiesResultMappings))

(def Documentation
  {(s/optional-key :summary) String
   (s/optional-key :description) String})

(def MethodDocumentation
  (merge Documentation
         {(s/optional-key :responses) {s/Int {:description String}}}))

(s/defschema MethodParameters
  (merge-with
   merge
   ResourceParameters               ; Method params can override these
   {(s/optional-key :parameters)
    {(s/optional-key :form) s/Any
     (s/optional-key :body) s/Any}}))

(s/defschema Method
  (merge Handler
         MethodParameters
         Produces
         Consumes
         MethodDocumentation
         {NamespacedKeyword s/Any}))

(s/defschema Methods
  {(s/optional-key :methods) ; nil, for example, has no methods.
   {s/Keyword Method}})

(defprotocol MethodCoercion
  (as-method-map [_] "Coerce to Method"))

(extend-protocol MethodCoercion
  clojure.lang.APersistentMap
  (as-method-map [m] m)
  String
  (as-method-map [o] {:handler o
                      :produces "text/plain"})
  Object
  (as-method-map [o] {:handler o
                      :produces "application/octet-stream"})
  nil
  (as-method-map [o] {:handler nil}))

(def MethodsMappings
  (merge {Method as-method-map
          HandlerFunction as-fn}
         RepresentationSeqMappings))

(def ResourceDocumentation (merge Documentation))

(def Resource
  (merge {(s/optional-key :collection?) Boolean
          (s/optional-key :exists?) Boolean
          (s/optional-key :id) s/Any}
         Properties
         ResourceParameters
         Produces
         Consumes
         Methods
         ResourceDocumentation
         {NamespacedKeyword s/Any}))

(def ResourceMappings
  (merge PropertiesMappings
         RepresentationSeqMappings
         MethodsMappings))

(def resource-coercer (sc/coercer Resource ResourceMappings))
