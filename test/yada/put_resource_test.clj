;; Copyright © 2015, JUXT LTD.

(ns yada.put-resource-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [clojure.java.io :as io]
   [juxt.iota :refer (given)]
   [manifold.stream :as s]
   [yada.test.util :refer [to-string]]
   [yada.yada :refer [yada]]))

(defn add-headers [request m]
  (merge-with merge
              request
              {:headers m}))

(deftest put-test
  (testing "string"
    (let [resource (atom "Bradley")
          handler (yada resource)]

      (given @(handler (request :get "/"))
        :status := 200
        :headers :⊃ {"content-length" 7
                     "content-type" "text/plain;charset=utf-8"}
        [:body to-string] := "Bradley")

      (given @(handler (-> (request :put "/" "Chelsea")
                           (add-headers {"content-type" "text/plain"})))
        :status := 204
        [:headers keys set] :⊅ #{"content-type"}
        [:headers keys set] :⊅ #{"content-length"}
        :body := nil)

      (is (= @resource "Chelsea"))

      (given @(handler (request :get "/"))
        :status := 200
        :headers :⊃ {"content-length" 7
                     "content-type" "text/plain;charset=utf-8"}
        [:body to-string] := "Chelsea")))

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
