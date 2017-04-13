(ns yada.custom-error-response-test
  (:require [clojure.test :refer :all]
            [byte-streams :as b]
            [ring.mock.request :refer [request]]
            [yada.yada :refer [yada handler resource]]))

(defn- error-resource
  [method exception]
  (resource
   {:methods
    {method
     {:produces "text/plain"
      :response (fn [ctx] (throw exception))}}
    :responses
    {500 {:produces "text/plain"
          :response (fn [ctx] "Error")}}}))

(deftest custom-error-response-test []
  (testing "GET custom error for java.lang.Exception"
    (try
      (let [handler (yada (error-resource :get (Exception. "Oh!")))
            response @(handler (request :get "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        ;; prevent stack trace in error report - just show an actionable message
        (let [error-handled? (nil? e)]
          (is error-handled? "java.lang.Exception not caught by handler")))))

  (testing "GET custom error for clojure.lang.ExceptionInfo"
    (try
      (let [handler (yada (error-resource :get (ex-info "Oh!" {})))
            response @(handler (request :get "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler")))))

  (testing "POST custom error for java.lang.Exception"
    (try
      (let [handler (yada (error-resource :post (Exception. "Oh!")))
            response @(handler (request :post "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "java.lang.Exception not caught by handler")))))

  (testing "POST custom error for clojure.lang.ExceptionInfo"
    (try
      (let [handler (yada (error-resource :post (ex-info "Oh!" {})))
            response @(handler (request :post "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler"))))))
