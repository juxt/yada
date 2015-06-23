;; Copyright © 2015, JUXT LTD.

(ns yada.collection-resource-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [clojure.java.io :as io]
   [yada.test.util :refer (given)]
   [yada.yada :refer [yada]]
   yada.collection-resource))

;; Collections can be resources too, we should test them

(deftest map-resource-test
  (testing "atom"
    (let [resource {:name "Frank"}
          handler (yada resource)
          request (request :get "/")
          response @(handler request)]

        (given response
          :status := 200
          :body :? string?))))
