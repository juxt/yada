(ns yada.redirect-test
  (:require
   [yada.redirect :refer :all]
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(deftest redirect-test
  (testing "302 with GET"
    (let [response
          (yada/response-for
           {:methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (redirect "http://example.com/canonical-location")))}}})]
      (is (= 302 (:status response)))
      (is (= "http://example.com/canonical-location" (get-in response [:headers "location"])))))

  (testing "303 with POST"
    (let [response
          (yada/response-for
           {:methods
            {:post
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (redirect "http://example.com/canonical-location")))}}}
           :post)]
      (is (= 303 (:status response)))
      (is (= "http://example.com/canonical-location" (get-in response [:headers "location"])))))

  (testing "302 with PUT, since PUT is idempotant"
    (let [response
          (yada/response-for
           {:methods
            {:put
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (redirect "http://example.com/canonical-location")))}}}
           :put)]
      (is (= 302 (:status response)))
      (is (= "http://example.com/canonical-location" (get-in response [:headers "location"])))))

  (testing "302 with DELETE, since DELETE is idempotant"
    (let [response
          (yada/response-for
           {:methods
            {:delete
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (redirect "http://example.com/canonical-location")))}}}
           :delete)]
      (is (= 302 (:status response)))
      (is (= "http://example.com/canonical-location" (get-in response [:headers "location"]))))))
