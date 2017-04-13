;; Copyright © 2015, JUXT LTD.

(ns yada.head-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :as yada :refer [yada]]))

(deftest head-test []
  (let [resource "Hello World!"
        handler (yada (merge (yada/as-resource resource)
                             {:produces {:media-type "text/plain"
                                         :charset "UTF-8"}}))
        request (request :head "/")
        response @(handler request)
        headers (:headers response)]

    (is (= 200 (:status response) ))
    (is (= "text/plain;charset=utf-8" (get headers "content-type")))
    
    (is (nil? (get headers "content-length"))) ; see rfc7231.html#section-3.3

    (is (nil? (:body response)))))

