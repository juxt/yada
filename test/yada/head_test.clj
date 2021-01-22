;; Copyright © 2014-2017, JUXT LTD.

(ns yada.head-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :as ring-mock]
   [yada.handler :refer [handler]]
   [yada.resource :refer [as-resource]]))

(deftest head-test []
  (let [resource "Hello World!"
        h (handler (merge (as-resource resource)
                          {:produces {:media-type "text/plain"
                                      :charset "UTF-8"}}))
        request (ring-mock/request :head "/")
        response @(h request)
        headers (:headers response)]

    (is (= 200 (:status response)))
    (is (= "text/plain;charset=utf-8" (get headers "content-type")))

    (is (nil? (get headers "content-length"))) ; see rfc7231.html#section-3.3

    (is (nil? (:body response)))))

(deftest head-content-type-not-unacceptable-test []
  (let [h (handler (merge (as-resource "Hello World!")
                          {:produces {:media-type "text/plain"
                                      :charset "UTF-8"}}))
        request (-> (ring-mock/request :head "/")
                    (ring-mock/header :accept "text/foo"))
        response @(h request)
        headers (:headers response)]

    (is (= 406 (:status response)))
    (is (nil? (get headers "content-type")))

    (is (nil? (get headers "content-length"))) ; see rfc7231.html#section-3.3

    (is (nil? (:body response)))))
