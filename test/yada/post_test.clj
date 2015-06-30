(ns yada.post-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [yada.yada :refer (yada)]
   [yada.test.util :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]))


(deftest post-test
  (let [handler (yada nil :post! {:status 201 :body "foo"})]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:body bs/to-string] := "foo")))

(deftest dynamic-post-test
  (let [handler (yada nil :post! (fn [ctx] {:status 201 :body "foo"}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:body bs/to-string] := "foo")))

(deftest multiple-headers-test
  (let [handler (yada nil :post! (fn [ctx] {:status 201 :headers {"set-cookie" ["a" "b"]}}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:headers "set-cookie"] := ["a" "b"])))
