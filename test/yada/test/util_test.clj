;; Copyright © 2015, JUXT LTD.

(ns yada.test.util-test
  (:require
   [yada.test.util :refer :all]
   [clojure.test :refer :all]))

(deftest myself
  (given :foo
    keyword? true
    identity :foo
    [str count] 4
    ))
