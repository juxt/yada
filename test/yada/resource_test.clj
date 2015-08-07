;; Copyright © 2015, JUXT LTD.

(ns yada.resource-test
  (:require
   [yada.resource :refer [coerce-etag-result]]
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]))

(deftest coerce-etag-result-test
  (is (= (coerce-etag-result "123" nil) "123"))
  (is (= (coerce-etag-result {:foo :bar} nil) "-907073541"))
  (is (= (coerce-etag-result [:foo :bar] nil) "-565349210")))
