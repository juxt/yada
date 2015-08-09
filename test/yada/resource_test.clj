;; Copyright © 2015, JUXT LTD.

(ns yada.resource-test
  (:require
   [yada.resource :refer [to-etag]]
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]))

(deftest to-etag-test
  (is (= (to-etag "123" nil) "1149470190"))
  (is (= (to-etag {:foo :bar} nil) "-907073541"))
  (is (= (to-etag [:foo :bar] nil) "-565349210")))
