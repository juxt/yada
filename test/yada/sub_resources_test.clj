(ns yada.sub-resources-test
  (:require
   [clojure.test :refer :all]
   [yada.handler :refer [handler]]
   [yada.resource :refer [as-resource]]
   [yada.test :refer [response-for]]))

(deftest schema-test
  (is
   (handler
    {:sub-resource (fn [ctx] (as-resource "Hello World!"))}))
  (is
   (= "Hello World!"
      (:body (response-for {:sub-resource (fn [ctx] (as-resource "Hello World!"))})))))
