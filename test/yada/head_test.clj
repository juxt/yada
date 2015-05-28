(ns yada.head-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.core :refer [yada]]
   [yada.test.util :refer (given)]))

(deftest head-test []
  (let [resource {:state "Hello World!"}
        handler (yada resource)
        request (request :head "/")
        response @(handler request)]
    (given response
      :status 200
      [:headers "content-type"] "text/plain"
      :body nil)))
