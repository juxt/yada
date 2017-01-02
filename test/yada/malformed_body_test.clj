(ns yada.malformed-body-test
  (:require [clojure.test :refer :all]
            [yada.resource :refer [resource]]
            [yada.handler :refer [handler]]
            [schema.core :as sc]
            [ring.mock.request :as mock]
            [clojure.edn :as edn]
            [byte-streams :as bs]))


(deftest schema-error-is-available-in-context-error
  (let [resource
        (resource {:consumes [{:media-type #{"application/edn" "application/x-www-form-urlencoded"}}]
                   :produces [{:media-type #{"application/edn"}}]
                   :methods {:post {:parameters {:body {:foo sc/Int}}
                                    :response identity}}
                   :responses {400 {:produces [{:media-type #{"application/edn"}}]
                                    :response (fn [ctx]
                                                (-> ctx :error ex-data :error))}}})
        handler (handler resource)
        edn-request (-> (mock/request :post "/" (pr-str {:foo :asdf}))
                        (mock/content-type "application/edn"))
        form-request (mock/request :post "/" {:foo :asdf})]

    (let [response @(handler edn-request)]
      (is (some? response))
      (is (= 400 (:status response)))
      (let [body (-> response :body bs/to-string edn/read-string)]
        (is (= '{:foo (not (integer? :asdf))} body))))

    (let [response @(handler form-request)]
      (is (some? response))
      (is (= 400 (:status response)))
      (let [body (-> response :body bs/to-string edn/read-string)]
        (is (= '{:foo (not (integer? :asdf))} body))))))


;; Demote to json ext
#_(deftest malformed-json-body
  (let [resource
        (resource {:consumes [{:media-type #{"application/json"}}]
                   :methods {:post {:parameters {:body {:foo sc/Int}}
                                    :response (fn [ctx] "OK")}}})
        handler (handler resource)]

    (testing "bad json gives 400 issue #54"
      (let [req (-> (mock/request :post "/" "{\"x\":\"1}")
                    (mock/content-type "application/json"))]
        (is (= (:status @(handler req)) 400))))))
