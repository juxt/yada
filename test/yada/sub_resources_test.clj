(ns yada.sub-resources-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(deftest schema-test
  (is
   (yada/resource
    {:sub-resource (fn [ctx] (yada/as-resource "Hello World!"))}))
  (is
   (= "Hello World!"
      (:body (yada/response-for {:sub-resource (fn [ctx] (yada/as-resource "Hello World!"))})))))
