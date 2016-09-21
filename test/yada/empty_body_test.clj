(ns yada.empty-body-test
  (:require [clojure.test :refer [deftest testing is]]
            [yada.resource :refer [resource]]
            [yada.handler :refer [handler]]
            [schema.core :as sc]
            [ring.mock.request :as mock]))

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
