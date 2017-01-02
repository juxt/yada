(ns yada.custom-error-response-test
  (:require [clojure.test :refer :all]
            [byte-streams :as b]
            [ring.mock.request :refer [request]]
            [yada.handler :refer [handler]]
            [yada.resource :refer [resource]]))

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
      (let [h (handler (error-resource :get (Exception. "Oh!")))
            response @(h (request :get "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        ;; prevent stack trace in error report - just show an actionable message
        (let [error-handled? (nil? e)]
          (is error-handled? "java.lang.Exception not caught by handler")))))

  (testing "GET custom error for clojure.lang.ExceptionInfo"
    (try
      (let [h (handler (error-resource :get (ex-info "Oh!" {})))
            response @(h (request :get "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler")))))

  (testing "POST custom error for java.lang.Exception"
    (try
      (let [h (handler (error-resource :post (Exception. "Oh!")))
            response @(h (request :post "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "java.lang.Exception not caught by handler")))))

  (testing "POST custom error for clojure.lang.ExceptionInfo"
    (try
      (let [h (handler (error-resource :post (ex-info "Oh!" {})))
            response @(h (request :post "/"))]
        (is (= 500 (:status response)))
        (is (= "Error" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler"))))))

(deftest custom-error-with-body
  (testing "GET custom error with response body [text/plain]"
    (try
      (let [h (handler (error-resource :get (ex-info "Oh!" {:status 400
                                                            :body "error response body"})))
            response @(h (request :get "/"))]
        (is (= 400 (:status response)))
        (is (= "error response body" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler")))))

  (testing "POST custom error with response body [text/plain]"
    (try
      (let [h (handler (error-resource :post (ex-info "Oh!" {:status 400
                                                             :body "error response body"})))
            response @(h (request :post "/"))]
        (is (= 400 (:status response)))
        (is (= "error response body" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler")))))

  (testing "GET custom error with response body [application/edn]"
    (try
      (let [resource (error-resource :get (ex-info "Oh!" {:status 400
                                                          :body {:message "custom error message"}}))
            resource' (assoc-in resource [:methods :get :produces] "application/edn")
            h (handler resource')
            response @(h (-> (request :get "/")
                             (assoc-in [:headers "accept"] "application/edn")))]
        (is (= 400 (:status response)))
        (is (= "application/edn" (get-in response [:headers "content-type"])))
        (is (= "{:message \"custom error message\"}\n" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler")))))

  (testing "POST custom error with response body [application/json]"
    (try
      (let [resource (error-resource :post (ex-info "Oh!" {:status 400
                                                           :body {:message "custom error message"}}))
            resource' (assoc-in resource [:methods :post :produces] "application/edn")
            h (handler resource')
            response @(h (-> (request :post "/")
                             (assoc-in [:headers "accept"] "application/edn")))]
        (is (= 400 (:status response)))
        (is (= "application/edn" (get-in response [:headers "content-type"])))
        (is (= "{:message \"custom error message\"}\n" (b/to-string (:body response)))))
      (catch Exception e
        (let [error-handled? (nil? e)]
          (is error-handled? "clojure.lang.ExceptionInfo not caught by handler"))))))
