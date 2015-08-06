;; Copyright © 2015, JUXT LTD.

(ns yada.resource-test
  (:require
   [yada.resource :refer [coerce-etag-result]]
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]))

(deftest coerce-etag-result-test
  (is (= (coerce-etag-result "123") "123"))
  (is (= (coerce-etag-result {:foo :bar}) "1766419479"))
  (is (= (coerce-etag-result [:foo :bar]) "1531896286")))
