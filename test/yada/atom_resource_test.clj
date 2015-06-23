;; Copyright © 2015, JUXT LTD.

(ns yada.atom-resource-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [clojure.java.io :as io]
   [yada.test.util :refer (given)]
   [yada.yada :refer [yada]]
   yada.atom-resource))

(deftest atom-test
  (testing "atom"
      (let [resource (atom {:name "Frank"})
            handler (yada resource)
            request (request :get "/")
            response @(handler request)]

        (given response
          :status := 200
          :headers := nil
          :body :? string?)

        )))
