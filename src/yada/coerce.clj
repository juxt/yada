;; Copyright © 2015, JUXT LTD.

(ns yada.coerce
  (:require
   [schema.core :as s]
   [schema.coerce :refer [coercer
                          string-coercion-matcher
                          string->keyword
                          keyword-enum-matcher
                          set-matcher]]
   [ring.swagger.coerce :as rsc]
   [clj-time.coerce :as time])
  (:import [schema.core RequiredKey OptionalKey]
           [clojure.lang Keyword]))

(defprotocol SetCoercion
  (to-set [_] "Coerce to a set, useful for a shorthand when specifying
  representation entries, which must always be coerced to sets."))

(extend-protocol SetCoercion
  java.util.Set
  (to-set [s] s)
  clojure.lang.Sequential
  (to-set [s] (set s))
  Object
  (to-set [o] #{o})
  nil
  (to-set [_] nil))

(defprotocol ListCoercion
  (to-list [_] "Coerce to a list, useful for a shorthand when specifying
  representation entries where ordering is relevant (languages)"))

(extend-protocol ListCoercion
  clojure.lang.Sequential
  (to-list [s] s)
  Object
  (to-list [o] [o])
  nil
  (to-list [_] nil))

;; Parameter coercions

(def +date-coercions+
  {s/Inst (comp time/to-date time/from-string)})

(defn coercion-matcher [schema]
  (or (string-coercion-matcher schema)
      (+date-coercions+ schema)))

(defprotocol ParameterKey
  "We could walk the parameters to find out whether they are ultimately
  keywords or strings, but this protocol achieves the same thing."
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

(defn- kw-map-matcher [schema]
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

(defn- multiple-args-matcher [schema]
  ;; TODO: Need to be a bit careful here, it could be a byte-array [byte].
  (when (instance? clojure.lang.APersistentVector schema)
    (fn [x]
      (if (not (sequential? x)) [x] x))))

(defn +parameter-key-coercions+
  "A matcher that coerces keywords and keyword enums from strings, and
  longs and doubles from numbers on the JVM (without losing precision)"
  [schema]
  (or
   (coercion-matcher schema)
   (kw-map-matcher schema)
   (multiple-args-matcher schema)))

