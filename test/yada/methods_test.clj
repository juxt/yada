;; Copyright © 2014-2017, JUXT LTD.

(ns yada.methods-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.resource :refer [resource]]
   [yada.handler :refer [handler]]
   [juxt.iota :refer [given]])
  (:import (java.net URI)))

(deftest post-test
  (let [h (handler
           (resource
            {:methods {:post
                       {:response (fn [ctx]
                                    (assoc (:response ctx)
                                           :status 201
                                           :body "foo"))}}}))
        response @(h (mock/request :post "/"))]

    (is (= 201 (:status response)))
    (is (= "foo" (bs/to-string (:body response))))))

(deftest post-uri-test
  (let [h (handler
           (resource
            {:methods {:post
                       {:response (fn [ctx]
                                    (URI. "http://localhost/new"))}}}))]
    (testing "same origin"
      (testing "no origin header"
        (given @(h (-> (mock/request :post "/")
                       (mock/header "referer" "http://localhost/some/other")))
               :status := 303
               [:headers "location"] := "http://localhost/new"))
      (testing "origin header"
        (given @(h (-> (mock/request :post "/")
                       (mock/header "origin" "http://localhost")
                       (mock/header "referer" "http://localhost/some/other")))
               :status := 303
               [:headers "location"] := "http://localhost/new")))
    (testing "cross origin"
      (testing "no headers"
        (given @(h (mock/request :post "/"))
               :status := 201
               [:headers "location"] := "http://localhost/new"))
      (testing "referer header"
        (given @(h (-> (mock/request :post "/")
                       (mock/header "referer" "http://example.com/some/other")))
               :status := 201
               [:headers "location"] := "http://localhost/new"))
      (testing "origin header"
        (given @(h (-> (mock/request :post "/")
                       (mock/header "origin" "http://example.com")
                       (mock/header "referer" "http://example.com/some/other")))
               :status := 201
               [:headers "location"] := "http://localhost/new")))))

(deftest dynamic-post-test
  (let [h (handler
           (resource
            {:methods {:post {:response (fn [ctx]
                                          (assoc (:response ctx)
                                                 :status 201 :body "foo"))}}}))
        response @(h (mock/request :post "/"))]

    (is (= 201 (:status response)))
    (is (= "foo" (bs/to-string (:body response))))))

(deftest multiple-headers-test
  (let [h
        (handler
         (resource
          {:methods
           {:post
            {:response
             (fn [ctx]
               (assoc (:response ctx)
                      :status 201 :headers {"set-cookie" ["a" "b"]}))}}}))
        response @(h (mock/request :post "/"))]
    (is (= 201 (:status response)))
    (is (= ["a" "b"] (get-in response [:headers "set-cookie"])))))

(deftest all-methods-test
  (let [h
        (handler
         (resource
          {:methods
           {:*
            {:response
             (fn [ctx]
               (-> ctx :method name)
               )}}}))
        response @(h (mock/request :brew "/"))]
    (is (= 200 (:status response)))
    (is (= "brew" (bs/to-string (:body response))))))

;; Allowed methods ---------------------------------------------------

;; To ensure coercion to StringResource which satisfies GET (tested
;; below)
#_(require 'yada.resources.string-resource)

#_(deftest allowed-methods-test
    (testing "methods-deduced"
      (are [r e] (= (:allowed-methods (yada r)) e)
        nil #{:get :head :options}
        "Hello" #{:get :head :options}
        (reify Get (GET [_ _] "foo")) #{:get :head :options}
        (reify
          Get (GET [_ _] "foo")
          Post (POST [_ _] "bar")) #{:get :post :head :options})))
