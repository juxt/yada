;; Copyright © 2015, JUXT LTD.

(ns yada.nil-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :as yada :refer [yada]]))

(deftest nil-test
  (testing "A nil resource should yield on nil"
    (doseq [method [:get :post :put]]
      (let [res (yada nil)]
        (try
          @(res (request method "/"))
          (catch clojure.lang.ExceptionInfo e
            (is (= {:error {:status 404}} (ex-data e)))))))))




