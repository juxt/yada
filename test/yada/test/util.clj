;; Copyright © 2015, JUXT LTD.

(ns yada.test.util
  (:require
   [clojure.test :refer :all]
   [clojure.set :as set]))

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
  (let [t (gensym)]
    `(do
       (let [~t ~v]
         ~@(for [[a b c] (partition 3 body)]
             (case b
               ;; Equals?
               := `(is (= ((as-test-function ~a) ~t) ~c))
               :!= `(is (not= ((as-test-function ~a) ~t) ~c))
               ;; Schema checks
               :- `(is (nil? (s/check ~c ((as-test-function ~a) ~t))))
               ;; Is?
               :? `(is (~c ((as-test-function ~a) ~t)))
               :!? `(is (not (~c ((as-test-function ~a) ~t))))
               ;; Matches regex?
               :# `(is (re-matches (re-pattern ~c) ((as-test-function ~a) ~t)))
               :!# `(is (not (re-matches (re-pattern ~c) ((as-test-function ~a) ~t))))
               ;; Is superset?
               :> `(is (set/superset? (set ((as-test-function ~a) ~t)) (set ~c)))
               :!> `(is (not (set/superset? (set ((as-test-function ~a) ~t)) (set ~c))))
               ;; Is subset?
               :< `(is (set/subset? (set ((as-test-function ~a) ~t)) (set ~c)))
               :!< `(is (not (set/subset? (set ((as-test-function ~a) ~t)) (set ~c))))))))))
