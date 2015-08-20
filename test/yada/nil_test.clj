;; Copyright © 2015, JUXT LTD.

(ns yada.nil-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :as yada]))

(deftest nil-test
  (testing "A nil resource should yield a 404"
    (let [res (yada/resource nil)
          resp @(res (request :get "/"))]
      (is (= 404 (:status resp))))))
