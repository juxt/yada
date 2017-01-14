;; Copyright © 2014-2017, JUXT LTD.

(ns yada.head-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.handler :refer [handler]]
   [yada.resource :refer [as-resource]]))

(deftest head-test []
  (let [resource "Hello World!"
        h (handler (merge (as-resource resource)
                          {:produces {:media-type "text/plain"
                                      :charset "UTF-8"}}))
        request (request :head "/")
        response @(h request)
        headers (:headers response)]

    (is (= 200 (:status response)))
    (is (= "text/plain;charset=utf-8" (get headers "content-type")))

    (is (nil? (get headers "content-length"))) ; see rfc7231.html#section-3.3

    (is (nil? (:body response)))))
