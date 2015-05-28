(ns yada.test.util
  (:require [clojure.test :refer :all]))

;; See the test at the end of this ns to understand the point of this code

(defprotocol TestClause
  (as-test-function [_] "Take the clause and return a function which is applied to the value under test"))

(extend-protocol TestClause
  clojure.lang.APersistentVector
  ;; A function application 'path', which is simply a composed function,
  ;; left-to-right rather than right-to-left.
  (as-test-function [v] (apply comp (map as-test-function (reverse v))))

  clojure.lang.Keyword
  (as-test-function [k] k)

  clojure.lang.Fn
  (as-test-function [f] f)

  String
  (as-test-function [s] #(get % s)))

(defmacro given [v & body]
  `(let [v# ~v] ; because we want to evaluate v just the once, not on every clause
     (are [x# y#] (= ((as-test-function x#) v#) y#)
       ~@body)))
