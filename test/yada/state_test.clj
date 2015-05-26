(ns yada.state-test
  (:require
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.core :refer [yada]]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (parse-date format-date)])
  (:import [java.util Date]))


;; Test a resource where the state is an actual file
(deftest file-test
  (let [resource {:state (io/file "test/yada/state/test.txt")}
        handler (yada resource)
        request (request :get "/")
        response @(handler request)]

    (testing "expectations of set-up"
      (is (.exists (:state resource)))
      (is (= (.getName (:state resource)) "test.txt")))

    (testing "response"
      (is (some? response))
      (is (= (:status response) 200))
      (is (= (get-in response [:headers "content-type"]) "text/plain"))
      (is (instance? java.io.File (:body response)))
      (is (= (get-in response [:headers "content-length"]) (.length (:state resource)))))

    (testing "last-modified"
      (is (= (get-in response [:headers "last-modified"]) "Sun, 24 May 2015 16:44:47 GMT"))
      (let [last-modified (parse-date (get-in response [:headers "last-modified"]))]
        (is (= (.getTime last-modified) (.lastModified (:state resource))))))

    (testing "conditional-response"
      (let [response @(handler (assoc-in request [:headers "if-modified-since"]
                                         (format-date (Date. (.lastModified (:state resource))))))]
        (is (= (:status response) 304))))))

;; Test a single resource. Note that this isn't a particularly useful
;; resource, because it contains no knowledge of when it was modified,
;; how big it is, etc.

(deftest resource-test
  (let [resource {:state (io/resource "public/css/fonts.css")}
        handler (yada resource)
        response @(handler (request :get "/"))]
    (is (some? response))
    (is (= (:status response) 200))
    (is (= (get-in response [:headers "content-type"]) "text/css"))
    (is (instance? java.io.BufferedInputStream (:body response)))))

;; TODO: Test conditional content
;; TODO: Serve up file images and other content required for the examples with yada - don't use bidi/resources
;; TODO: yada should replace bidi's resources, files, etc.  and do a far better job
;; TODO: Observation: wrap-not-modified works on the response only - i.e. it still causes the handler to handle the request (and do work) on the server.
