;; Copyright © 2016, JUXT LTD.

(ns yada.security-test
  (:require
   [yada.test :refer [request-for response-for]]
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]))

(deftest www-authenticate-test
  (testing "www-authenticate header"
    (let [response
          (response-for
           {:access-control
            {:scheme "Basic"
             :verify (fn [[user password]]
                       (when (= [user password] ["scott" "tiger"])
                         {:user "scott"
                          :roles #{"secret/view"}}))
             :authorization {:methods {:get "secret/view"}}}

            :methods
            {:get {:produces "text/plain"
                   :response (fn [ctx] "secret")}}}

           :get "/" {})]
      (is (= 401 (:status response)))
      (is (= ["Basic realm=\"default\""] (get-in response [:headers "www-authenticate"])))))

  (testing "No www-authenticate header produced for non-string scheme"
    (let [response
          (response-for
           {:access-control
            {:scheme :custom
             :verify (constantly nil)
             :authorization {:methods {:get "secret/view"}}}
            :methods
            {:get {:produces "text/plain"
                   :response (fn [ctx] "secret")}}}

           :get "/" {})]
      (is (= 401 (:status response)))
      (is (nil? (get-in response [:headers "www-authenticate"])))))

  (testing "authentication schemes as a function"
    (let [response
          (response-for
           {:access-control
            {:authentication-schemes (fn [ctx] [{:scheme "Basic"}])
             :authorization {:methods {:get (fn [ctx] "user")}}}

            :methods
            {:get {:produces "text/plain"
                   :response (fn [ctx] "secret")}}}

           :get "/" {})]
      (is (= 401 (:status response)))
      (is (= ["Basic realm=\"default\""] (get-in response [:headers "www-authenticate"]))))))
