(ns yada.state-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.core :refer [yada]]
   [ring.mock.request :refer [request]])
  (:import [java.util Date]))


(deftest file-test
  (let [resource {:state (io/file "test/yada/state/test.txt")}
        handler (yada resource)
        response @(handler (request :get "/"))]
    (is (.exists (:state resource)))
    (is (= (.getName (:state resource)) "test.txt"))
    (is (some? response))
    (is (= (:status response) 200))
    (is (= (get-in response [:headers "content-type"]) "text/plain"))
    (is (instance? java.io.File (:body response)))
    (is (= (get-in response [:headers "last-modified"]) "Fri, 22 May 2015 07:30:45 GMT"))
    (is (= (get-in response [:headers "content-length"]) 26))
    ))
