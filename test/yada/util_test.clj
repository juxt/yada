;; Copyright © 2014-2017, JUXT LTD.

(ns yada.util-test
  (:require
   [clojure.test :refer :all]
   [yada.util :refer (best best-by)]))

(deftest best-test
  (is (= (best [3 2 3 nil 19]) 19))
  (is (= (best (comp - compare) [3 2 3 nil 19]) nil)))

(deftest best-by-test
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [nil 0] [19 10]]) [nil 0]))
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [-2 0] [19 10]]) [-2 0])))
