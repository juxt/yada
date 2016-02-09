;; Copyright © 2015, JUXT LTD.

(ns yada.methods-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.protocols :as p]
   [yada.resource :refer [resource]]
   [yada.yada :as yada :refer [yada]]))

(deftest post-test
  (let [handler (yada
                 (resource
                  {:methods {:post
                             {:response (fn [ctx]
                                          (assoc (:response ctx)
                                                 :status 201
                                                 :body "foo"))}}}))
        response @(handler (mock/request :post "/"))]

    (is (= 201 (:status response) ))
    (is (= "foo" (bs/to-string (:body response))))))

(deftest dynamic-post-test
  (let [handler (yada
                 (resource
                  {:methods {:post {:response (fn [ctx]
                                                (assoc (:response ctx)
                                                       :status 201 :body "foo"))}}}))
        response @(handler (mock/request :post "/"))]

    (is (= 201 (:status response)))
    (is (= "foo" (bs/to-string (:body response))))))

(deftest multiple-headers-test
  (let [handler
        (yada
         (resource
          {:methods
           {:post
            {:response
             (fn [ctx]
               (assoc (:response ctx)
                      :status 201 :headers {"set-cookie" ["a" "b"]}))}}}))
        response @(handler (mock/request :post "/"))]
    (is (= 201 (:status response)))
    (is (= ["a" "b"] (get-in response [:headers "set-cookie"])))))

(deftest all-methods-test
  (let [handler
        (yada
         (resource
          {:methods
           {:*
            {:response
             (fn [ctx]
               (-> ctx :method name)
               )}}}))
        response @(handler (mock/request :brew "/"))]
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
