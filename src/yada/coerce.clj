;; Copyright © 2014-2017, JUXT LTD.

(ns yada.coerce)

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
