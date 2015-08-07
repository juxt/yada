;; Copyright © 2015, JUXT LTD.

(ns yada.methods-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [juxt.iota :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.resources.misc :refer (just-methods)]
   [yada.resource :refer [ResourceEntityTag]]
   [yada.yada :as yada]))

(deftest post-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:response  (fn [ctx]
                                      (assoc (:response ctx)
                                             :status 201
                                             :body "foo"))}))]
    (given @(handler (mock/request :post "/"))
      :status := 201
      [:body bs/to-string] := "foo"
      ;;clojure.pprint/pprint := nil
      )))

(deftest dynamic-post-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:response (fn [ctx]
                                     (assoc (:response ctx)
                                            :status 201 :body "foo"
                                            ))}))]
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


#_(deftest etag-test
  (let [handler (yada/resource (reify
                                 ResourceEntityTag
                                 (etag [_ ctx] nil)
                                 ))]
    (given @(handler (mock/request :get "/"))
      :status := 200
      ))
  )
