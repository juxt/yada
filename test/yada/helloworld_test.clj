(ns yada.helloworld-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [yada.yada :as yada]
   [juxt.iota :refer (given)]
   [yada.dev.user-manual :as tutorial]
   [ring.mock.request :refer [request]]))

;; Test the Hello World tutorial

(deftest string-test
  (let [resource tutorial/hello]
    (given @(resource (request :get "/"))
      :status := 200
      ;; TODO: Test a lot more, like content-type
      [:body #(bs/convert % String)] := "Hello World!\n")))

;; TODO: conditional request

;; TODO: mutation

;; TODO: head request

(deftest parameters-test
  (let [resource tutorial/hello-parameters]
    (given @(resource (request :get "/?p=Ken"))
      :status := 200
      ;; TODO: Test a lot more, like content-type
      [:body #(bs/convert % String)] := "Hello Ken!\n")))

;; TODO: content negotiation

(deftest content-negotiation-test
  (let [resource tutorial/hello-languages]
    (testing "default is Simplified Chinese"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")))
        :status := 200
        [:headers "content-language"] := "zh-ch"
        ;; TODO: Test a lot more, like content-type
        [:body #(bs/convert % String)] := "你好世界!\n"))

    (testing "English is available on request"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")
                            (assoc-in [:headers "accept-language"] "en")))
        :status := 200
        ;; TODO: Test a lot more, like content-type
        [:headers "content-language"] := "en"
        [:body #(bs/convert % String)] := "Hello World!\n"))))

;; TODO: swagger
