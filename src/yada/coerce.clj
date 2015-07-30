;; Copyright © 2015, JUXT LTD.

(ns yada.coerce
  (:require
   [schema.coerce :refer (string-coercion-matcher)]
   [clj-time.coerce :as time]
   [schema.core :as s]))

(def +date-coercions+
  {s/Inst (comp time/to-date time/from-string)})

(defn coercion-matcher [schema]
  (or (string-coercion-matcher schema)
      (+date-coercions+ schema)))

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
