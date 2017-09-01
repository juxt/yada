;; Copyright © 2014-2017, JUXT LTD.

(ns yada.empty-body-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [ring.mock.request :as mock]
   [schema.core :as sc]
   [yada.handler :refer [handler]]
   [yada.resource :refer [resource]]))

(deftest no-form-body
  (let [resource
        (resource {:consumes [{:media-type #{"application/x-www-form-urlencoded"}}]
                   :methods {:post {:parameters {:body {:foo sc/Int}}
                                    :response (fn [ctx] "OK")}}})
        handler (handler resource)]

    (testing "no form body gives 400 issue #110"
      (let [req (-> (mock/request :post "/" "")
                    (mock/content-type "application/x-www-form-urlencoded"))]
        (is (= (:status @(handler req)) 400))))))

(deftest no-form-parameters
  (let [resource
        (resource {:consumes [{:media-type #{"application/x-www-form-urlencoded"}}]
                   :methods {:post {:parameters {:form {:foo sc/Int}}
                                    :response (fn [ctx] "OK")}}})
        handler (handler resource)]

    (testing "no form body gives 400 issue #110"
      (let [req (-> (mock/request :post "/" "")
                    (mock/content-type "application/x-www-form-urlencoded"))]
        (is (= (:status @(handler req)) 400))))))

(deftest no-edn-body
  (let [resource
        (resource {:consumes [{:media-type #{"application/edn"}}]
                   :methods {:post {:parameters {:body {:foo sc/Int}}
                                    :response (fn [ctx] "OK")}}})
        handler (handler resource)]

    (testing "no edn body gives 400 issue #110"
      (let [req (-> (mock/request :post "/" "")
                    (mock/content-type "application/edn"))]
        (is (= (:status @(handler req)) 400))))))

(deftest no-json-body
  (let [resource
        (resource {:consumes [{:media-type #{"application/json"}}]
                   :methods {:post {:parameters {:body {:foo sc/Int}}
                                    :response (fn [ctx] "OK")}}})
        handler (handler resource)]

    (testing "no json body gives 400 issue #110"
      (let [req (-> (mock/request :post "/" "")
                    (mock/content-type "application/json"))]
        (is (= (:status @(handler req)) 400))))))
