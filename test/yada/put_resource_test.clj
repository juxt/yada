;; Copyright © 2015, JUXT LTD.

(ns yada.put-resource-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [clojure.java.io :as io]
   [manifold.stream :as s]
   [yada.test-util :refer [to-string]]
   [yada.yada :refer [yada]]))

(defn add-headers [request m]
  (merge-with merge request {:headers m}))

(deftest put-test
  (testing "string"
    (let [resource (atom "Bradley")
          handler (yada resource)
          response @(handler (request :get "/"))
          headers (:headers response)]

      (is (= 200 (:status response)))
      (is (= {"content-length" 7
              "content-type" "text/plain;charset=utf-8"}
             (select-keys headers ["content-length" "content-type"])))
      (is (= "Bradley" (to-string (:body response))))

      (let [response @(handler (-> (request :put "/" {:value "Chelsea"})))]
        (is (= 204 (:status response)))
        (is (= (contains? (set (keys (:headers response))) "content-type")))
        (is (= (contains? (set (keys (:headers response))) "content-length")))
        (is (nil? (:body response))))
      
      (is (= @resource "Chelsea"))

      (let [response @(handler (request :get "/"))]
        (is (= 200 (:status response)))
        (is (= {"content-length" 7
                "content-type" "text/plain;charset=utf-8"}
               (select-keys (:headers response) ["content-length" "content-type"])))
        (is (= "Chelsea" (to-string (:body response)))))))

  #_(testing "atom"
      (let [resource (atom {:name "Frank"})
            handler (yada resource)
            request (request :get "/")
            response @(handler request)]

        (given response
               :status := 200
               :headers :> {"content-length" 16
                            "content-type" "application/edn"}
               :body :? string?)

        )))
