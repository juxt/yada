;; Copyright © 2015, JUXT LTD.

(ns yada.cors-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [ring.mock.request :as mock]
   [schema.test :refer [deftest]]
   [yada.yada :refer [resource as-resource yada]]))

(deftest cors-test
  (let [res (resource {:methods {:get "Hello"}
                       :access-control {:allow-origin "*"}})
        handler (yada res)
        resp @(handler (mock/request :get "/"))]
    (is (contains? (set (keys (:headers resp))) "access-control-allow-origin"))
    (is (= "*" (get-in resp [:headers "access-control-allow-origin"])))))
