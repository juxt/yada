;; Copyright © 2015, JUXT LTD.

(ns yada.trace-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.core :refer [yada]]
   [yada.test.util :refer (given)]))

(deftest trace-test []
  (testing "Normal operation"
    (let [resource {}
          handler (yada resource)
          request (merge (request :trace "/")
                         {:body (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))})
          response @(handler request)]
      (given response
        :status 200
        [:body #(.endsWith % "Hello World!")] true)))

  (testing "TRACE disabled"
    (let [resource {:methods [:get :head]}
          handler (yada resource)
          request (request :trace "/")
          response @(handler request)]
      (given response
        :status 405))))
