;; Copyright © 2014-2017, JUXT LTD.

(ns yada.function-resource-test
  (:require
   [byte-streams :as b]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.handler :refer [handler]]))

(deftest function-test
  (testing "Producing a Java string implies utf-8 charset"
    (let [handler (handler (fn [ctx] "Hello World!"))
          request (request :get "/")
          response @(handler request)]
      (is (= 200 (:status response)))
      (is (= "Hello World!" (b/to-string (:body response)))))))
