;; Copyright © 2015, JUXT LTD.

(ns yada.methods-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [juxt.iota :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.methods :refer (Get)]
   [yada.resources.misc :refer (just-methods)]
   [yada.resource :refer [ResourceEntityTag]]
   [yada.yada :as yada]))

(deftest post-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:response (fn [ctx]
                                     (assoc (:response ctx)
                                            :status 201
                                            :body "foo"))}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:body bs/to-string] := "foo")))

(deftest dynamic-post-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:response (fn [ctx]
                                     (assoc (:response ctx)
                                            :status 201 :body "foo"))}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:body bs/to-string] := "foo")))

(deftest multiple-headers-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:response (fn [ctx] (assoc (:response ctx)
                                                   :status 201 :headers {"set-cookie" ["a" "b"]}))}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:headers "set-cookie"] := ["a" "b"])))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"-?\d+" etag)))

(defrecord ETagTestResource [v]
  ResourceEntityTag
  (etag [_ ctx] @v)
  Get
  (GET [_ ctx] "foo"))

(deftest etag-test
  (testing "etags-identical-for-consecutive-gets"
    (let [v (atom 1)
          handler (yada/resource (->ETagTestResource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :get "/"))]
      (given [r1 r2]
        [first :status] := 200
        [second :status] := 200
        [first :headers "etag"] :? etag?
        [second :headers "etag"] :? etag?)
      ;; ETags are the same in both responses
      (is (= (get-in r1 [:headers "etag"])
             (get-in r2 [:headers "etag"]))))))


#_(let [e1 (get-in response [:headers "etag"])])
