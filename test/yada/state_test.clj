(ns yada.state-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.core :refer [yada]]
   [ring.mock.request :refer [request]]))


(deftest file-test
  (let [resource {:state (io/file "test/test.txt")}
        handler (yada resource)
        response @(handler (request :get "/"))]
    (is (.exists (:state resource)))
    (is (= (.getName (:state resource)) "test.txt"))
    (is (some? response))
    (is (= (:status response) 200))
    (is (= (get-in response [:headers "content-type"]) "text/plain"))
    (is (is (instance? java.io.File (:body response))))))
