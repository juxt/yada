;; Copyright © 2014-2017, JUXT LTD.

(ns ^{:doc "This namespace provides the coercions to transform a wide\nvariety of shorthand descriptions of a resource into a canonical\nresource map. This allows the rest of the yada code-base to remain\nagnostic to the syntax of shorthand forms
which significantly\nsimplifies coding while giving the author of yada resources the\nconvenience of terse
expressive short-hand descriptions."}
    yada.schema
    (:refer-clojure :exclude [boolean?])
    (:require
     [schema.coerce :as sc]
     [schema.core :as s]
     [schema.utils :refer [error?]]
     [yada.charset :refer [to-charset-map]]
     [yada.media-type :as mt]
     [yada.representation :as rep]
     [yada.util :refer [disjoint*?]])
    (:import java.util.Date
             yada.charset.CharsetMap
             yada.media_type.MediaTypeMap
             yada.representation.LanguageMap))

(s/defschema NamespacedKeyword
  (s/constrained s/Keyword namespace))

(s/defschema NamespacedEntries
  {NamespacedKeyword s/Any})

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
    (s/optional-key :language) LanguageMap
    (s/optional-key :encoding) String}
   not-empty))

(s/defschema RepresentationSet
  (s/constrained
   {:media-type #{MediaTypeMap}
    (s/optional-key :charset) #{CharsetMap}
    (s/optional-key :language) #{LanguageMap}
    (s/optional-key :encoding) #{String}}
   not-empty))

;; representation-seq & merge-representation are written due to short file
;; length limits on ecryptfs. Using a pulled out function & nested maps results
;; in a smaller .class
;; see: https://github.com/juxt/edge/issues/22
(defn- merge-representation
  [media-type charset language encoding]
  (merge
   (when media-type {:media-type media-type})
   (when charset {:charset charset})
   (when language {:language language})
   (when encoding {:encoding encoding})))

(defn representation-seq
  "Return a sequence of all possible individual representations from the
  result of coerce-representations."
  [reps]
  (mapcat
    (fn [rep]
      (mapcat
        (fn [media-type]
          (mapcat
            (fn [charset]
              (mapcat
                (fn [language]
                  (map
                    (fn [encoding]
                      (merge-representation media-type charset language encoding))
                    (or (:encoding rep) [nil])))
                (or (:language rep) [nil])))
            (or (:charset rep) [nil])))
        (or (:media-type rep) [nil])))
    reps))

(defprotocol MediaTypeCoercion
  (as-media-type [_] ""))

(defprotocol RepresentationSetCoercion
  (as-representation-set [_] ""))

(extend-protocol MediaTypeCoercion
  MediaTypeMap
  (as-media-type [mt] mt)
  String
  (as-media-type [s] (mt/string->media-type s)))

(defprotocol LanguageMapCoercion
  (as-language-map [_] ""))

(extend-protocol LanguageMapCoercion
  LanguageMap
  (as-language-map [m] m)
  String
  (as-language-map [s] (rep/parse-language s)))

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
   CharsetMap to-charset-map
   #{LanguageMap} as-set
   LanguageMap as-language-map
   #{String} as-set})

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

(defprotocol FunctionCoercion
  (as-fn [_] "Coerce to function"))

(extend-protocol FunctionCoercion
  clojure.lang.Var
  (as-fn [f] f)
  clojure.lang.Fn
  (as-fn [f] f)
  Object
  (as-fn [o] (constantly o))
  nil
  (as-fn [_] (constantly nil)))

(s/defschema Context {})

(defn resolve-dynamic
  "P"
  [f ctx]
  (if (fn? f)
    (f ctx)
    f))

(defmacro maybe-dynamic
  "Return a schema that allows for a value to be dynamic, resolved at
  request-time with resolve-dynamic."
  [t]
  `(s/conditional
    fn? (s/=> ~t Context)
    :else ~t))

(s/defschema ContextFunction
  (s/=> s/Any Context))

(s/defschema Produces
  {(s/optional-key :produces) (maybe-dynamic [Representation])})

(s/defschema Consumes
  {(s/optional-key :consumes) (maybe-dynamic [Representation])})

(s/defschema Response
  {:response ContextFunction})

(s/defschema PropertiesResult
  (merge {(s/optional-key :last-modified) s/Inst
          (s/optional-key :version) s/Any
          (s/optional-key :exists?) s/Bool}
         NamespacedEntries))

(s/defschema Properties
  {(s/optional-key :properties) (maybe-dynamic PropertiesResult)})

(def PropertiesMappings {})

(def PropertiesResultMappings {Date #(condp instance? %
                                       Long (Date. ^Long %)
                                       %)})

(def properties-result-coercer (sc/coercer PropertiesResult PropertiesResultMappings))

(def CommonDocumentation
  {(s/optional-key :description) String
   (s/optional-key :summary) String})

(defn wildcard? "is this the wildcard response" [fn]
  (= fn *))

(s/defschema Statii (s/conditional integer? s/Int set? #{s/Int} fn? (s/pred wildcard?)))

(s/defschema MethodDocumentation
  (merge CommonDocumentation
         {(s/optional-key :responses) {Statii (merge {:description String}
                                                     NamespacedEntries)}}))

(s/defschema MethodParameters
  (merge-with
   merge
   ResourceParameters               ; Method params can override these
   {(s/optional-key :parameters)
    {(s/optional-key :form) s/Any
     (s/optional-key :body) s/Any}}))

(s/defschema Consumer
  "A consumer is a reducing function that is called with the request's
  body payload chunks. Alternately, a resource can define a multipart consumer
  implementing `yada.multipart/PartConsumer` which will be used for multipart
  form-data requests."
  {(s/optional-key :consumer) (s/=> Context Context s/Any)
   (s/optional-key :part-consumer) s/Any})

(s/defschema MethodParametersCoercionMatchers
  "A map of coercion matchers as required by `schema.coerce/coercer`."
  {(s/optional-key :coercion-matchers) {(s/optional-key :path) (s/=> s/Any s/Any)
                                        (s/optional-key :query) (s/=> s/Any s/Any)
                                        (s/optional-key :body) (s/=> s/Any s/Any)
                                        (s/optional-key :form) (s/=> s/Any s/Any)}})

(def Realm s/Str)

(s/defschema Restriction
  {:realm Realm
   (s/optional-key :role) s/Keyword})

(s/defschema Authorization
  {(s/optional-key :restrict) [Restriction]})

(def AuthorizationMappings
  {[Restriction] (fn [x] (cond (map? x) (vector x)
                               :otherwise x))})

(s/defschema Method
  (merge Response
         MethodParameters
         MethodParametersCoercionMatchers
         Produces
         Consumes
         Consumer
         Authorization
         MethodDocumentation
         NamespacedEntries))

(s/defschema Methods
  {(s/optional-key :methods)
   {s/Keyword Method}})

(defprotocol MethodCoercion
  (as-method-map [_] "Coerce to Method"))

(extend-protocol MethodCoercion
  clojure.lang.APersistentMap
  (as-method-map [m] m)
  String
  (as-method-map [o] {:response o
                      :produces "text/plain"})
  Object
  (as-method-map [o] {:response o})

  nil
  (as-method-map [o] {:response nil}))

(def MethodsMappings
  (merge
   {Method as-method-map
    ContextFunction as-fn}
   RepresentationSeqMappings
   AuthorizationMappings))

(s/defschema Responses
  {(s/optional-key :responses)
   (s/constrained
     {Statii (merge
               {(s/optional-key :description) s/Str}
               Produces
               Response
               NamespacedEntries)}
     disjoint*?)})


;; Many HTTP headers are comma separated. We should accept these
;; verbatim strings in our schema.

(s/defschema Strings [s/Str])
(s/defschema StringSet #{s/Str})
(s/defschema Keywords [s/Keyword])

(def AuthScheme
  (merge
   {(s/optional-key :scheme) (s/cond-pre s/Keyword s/Str)
    (s/optional-key :verify) s/Any}
   NamespacedEntries))

(s/defschema AuthSchemes
  {(s/optional-key :authentication-schemes) (maybe-dynamic [AuthScheme])})

;; Authorization can contain any content because it is up to the
;; authorization interceptor, which is pluggable.
(s/defschema Authorization
  {(s/optional-key :authorization) s/Any})

(s/defschema RealmValue
  (merge AuthSchemes Authorization NamespacedEntries))

(s/defschema Realms
  {(s/optional-key :realms)
   {Realm RealmValue}})

(s/defschema Cors
  {(s/optional-key :allow-origin) (s/conditional fn? (s/=> (s/conditional ifn? #{s/Str} :else s/Str) Context) :else StringSet)
   (s/optional-key :allow-credentials) s/Bool
   (s/optional-key :expose-headers) (maybe-dynamic Strings)
   (s/optional-key :max-age) (maybe-dynamic s/Int)
   (s/optional-key :allow-methods) (maybe-dynamic Keywords)
   (s/optional-key :allow-headers) (maybe-dynamic Strings)})

(s/defschema AccessControlValue
  (merge Realms Cors NamespacedEntries))

(s/defschema AccessControl
  {(s/optional-key :access-control) AccessControlValue})

;; Here is some tricky code, caused by the necessity to compose the
;; 'data macros'. The realm short-hand has to rewrite the data, which
;; may include the scheme in short or long form, which makes it
;; difficult to find an approach that preserves schema integrity while
;; decoupling the data macros from each other. The consequence is that
;; these mappings have some intimate knowledge of each other's
;; structure, but the trade-off of more difficult maintenance is
;; deemed worth it for the value to the user of being able to use
;; short-hands. For an explanation of data macros, see
;; https://blog.juxt.pro/posts/data-macros.html

(def SingleRealmMapping
  {AccessControlValue
   (fn [x]
     (if-not (:realms x)
       (-> x
           ;; Merge everything we want to KEEP from the realm
           (merge {:realms {(or (:realm x) "default")
                            (select-keys x [:authentication-schemes :verify :scheme :authorization])}})
           ;; Remove anything we want to REMOVE from the rest of the
           ;; access-control definition
           (dissoc :realm :scheme :verify :authentication-schemes :authorization))
       x))})

(def SingleSchemeMapping
  {RealmValue
   (fn [x]
     (if (:scheme x)
       (-> x
           ;; Merge in a :authentication-schemes entry with a single
           ;; scheme containing the :scheme and :verify entries
           (merge {:authentication-schemes [(select-keys x [:scheme :verify])]})
           ;; Remove the :scheme and :verify keys
           (dissoc :scheme :verify))
       x))})

(def HeaderMappings
  {StringSet (fn [x]
               (cond
                 (string? x) (set (clojure.string/split x #"\s*,\s*"))
                 :otherwise (set (map str x))))
   Strings (fn [x]
             (cond
               (string? x) (clojure.string/split x #"\s*,\s*")
               :otherwise (map str x)))
   Keywords (fn [x]
              (cond
                (string? x) (map keyword (clojure.string/split x #"\s*,\s*"))
                :otherwise (vec x)))})

(def AccessControlMappings
  (merge SingleRealmMapping
         SingleSchemeMapping
         HeaderMappings
         {ContextFunction as-fn}))

(s/defschema ResourceDocumentation CommonDocumentation)

(s/defschema SecurityHeaders
  {(s/optional-key :strict-transport-security) {:max-age s/Num}
   (s/optional-key :content-security-policy) s/Str
   (s/optional-key :x-frame-options) s/Str
   (s/optional-key :xss-protection) s/Str})

(s/defschema Logger
  {(s/optional-key :logger) (s/=> s/Any Context)})

(s/defschema InterceptorChain
  {(s/optional-key :interceptor-chain) [s/Any]
   (s/optional-key :error-interceptor-chain) [s/Any]})

(s/defschema Resource
  (merge {(s/optional-key :id) s/Any}
         ResourceDocumentation
         AccessControl
         Properties
         ResourceParameters
         Produces
         Consumes
         Methods
         Responses
         SecurityHeaders
         {(s/optional-key :path-info?) Boolean
          (s/optional-key :sub-resource) (s/=> Resource Context)}
         Logger
         InterceptorChain
         NamespacedEntries))

(s/defschema ResourceMappings
  (merge PropertiesMappings
         RepresentationSeqMappings
         MethodsMappings
         AccessControlMappings))

(def resource-coercer
  (sc/coercer Resource
              (merge ResourceMappings
                     {Resource (fn [m]
                                 (let [r (:response m)]
                                   (cond-> m
                                     r (assoc-in [:methods :get :response] r)
                                     true (dissoc :response))))})))


;; Handler ---------------------------------------------------------

(s/defschema HandlerModel
  {:id s/Any
   :resource s/Any ; TODO: if we fold yada.schema into yada.resource and yada.handler we'll be able to reference Resource
   (s/optional-key :parent) s/Any ; as TODO above
   :allowed-methods #{s/Keyword}
   :known-methods {s/Keyword s/Any}
   :interceptor-chain [(s/=> Context Context)]
   :error-interceptor-chain [(s/=> Context Context)]
   (s/optional-key :path-info?) s/Bool})
