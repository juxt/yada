;; Copyright © 2015, JUXT LTD.

(ns yada.trace-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :as yada]
   [juxt.iota :refer (given)]))

;; For now disable TRACE - need to think a better way of returning a d/success from the chain, prior to response formation

#_(deftest trace-test []
  (testing "Normal operation"
    (let [resource "Hello World!"
          handler (yada resource)
          request (merge (request :trace "/")
                         {:body (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))})
          response @(handler request)]
      (given response
        :status := 200
        :body :? #(.endsWith % "Hello World!"))))

  ;; TODO: TRACE needs to be documented
  #_(testing "TRACE disabled"
    (let [handler (yada "Hello World!" {:trace false})
          request (request :trace "/")
          response @(handler request)]
      (given response
        :status := 405))))
