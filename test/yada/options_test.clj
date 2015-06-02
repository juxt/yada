;; Copyright © 2015, JUXT LTD.

(ns yada.options-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.core :refer [yada]]
   [yada.test.util :refer (given)]))

(deftest options-test []
  (testing "Server capabilities"
    (let [resource {}
          handler (yada resource)
          request (request :options "*")
          response @(handler request)]
      (given response
        :status := 200)))

  (testing "Resource capabilities"
    (let [resource {}
          handler (yada resource)
          request (request :options "/")
          response @(handler request)]
      (given response
        :status := 200
        :headers := {"accept" "(TODO)"}))))
