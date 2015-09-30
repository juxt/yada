(ns yada.schema
  (:require
   [plumbing.core]
   [schema.core :as s #?@(:cljs [:include-macros true])]
   [schema.coerce :refer [coercer
                          string->keyword
                          keyword-enum-matcher
                          set-matcher
                          json-coercion-matcher]])
  (:import [schema.core RequiredKey OptionalKey]
           [clojure.lang Keyword]))

(def +x-json-coercions+
  schema.coerce/+json-coercions+)

(defprotocol ParameterKey
  (to-key [_])
  (to-fn [_]))

(extend-protocol ParameterKey
  OptionalKey
  (to-key [o] (to-key (:k o)))
  (to-fn [o] (to-fn (:k o)))
  RequiredKey
  (to-key [o] (to-key (:k o)))
  (to-fn [o] (to-fn (:k o)))
  Keyword
  (to-key [k] (name k))
  (to-fn [k] string->keyword)
  String
  (to-key [s] s)
  (to-fn [s] identity))

(defn kw-map-matcher [schema]
  (if (instance? clojure.lang.APersistentMap schema)
    (let [fns (into {}
                    (for [k (keys schema)]
                      [(to-key k) (to-fn k)]))]
      (fn [x]
        (if (map? x)
          (->> x
               (map (fn [[k v]] [((get fns k identity) k) v]))
               (into {}))
          x)))))

(defn keyword->string [k]
  (if (keyword? k) (name k) k))

(defn multiple-args-matcher [schema]
  (when (instance? clojure.lang.APersistentVector schema)
    (fn [x]
      (if (not (sequential? x)) [x] x))))

(defn x-json-coercion-matcher
  "A matcher that coerces keywords and keyword enums from strings, and longs and doubles
     from numbers on the JVM (without losing precision)"
  [schema]
  (or (+x-json-coercions+ schema)
      (keyword-enum-matcher schema)
      (set-matcher schema)
      (multiple-args-matcher schema)
      (kw-map-matcher schema)))

((coercer {(s/optional-key "first.name") s/Str
           :surname s/Str
           :phone [s/Str]
           } x-json-coercion-matcher)
 {"phone" ["789"] "first.name" "Frank" "surname" "Briggs"}
 )
