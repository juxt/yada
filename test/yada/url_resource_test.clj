;; Copyright © 2014-2017, JUXT LTD.

(ns yada.url-resource-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [yada.yada :refer [handler]])
  (:import java.io.BufferedInputStream))

;; Test a single Java resource. Note that this isn't a particularly useful
;; resource, because it contains no knowledge of when it was modified,
;; how big it is, etc. (unless we can infer where it came from, if jar,
;; use the file-size stored in the java.util.zip.JarEntry for
;; content-length.)

(deftest resource-test
  (let [resource (io/resource "yada/test.css")
        handler (handler resource)]

    (let [response @(handler (mock/request :get "/"))]
      (is (some? response))
      (is (= 200 (:status response)))
      (is (not (nil? [:headers "content-type"])))
      (is (= "text/css;charset=utf-8" (get-in response [:headers "content-type"])))
      (is (not (nil? [:headers "content-length"])))
      (is (instance? BufferedInputStream (:body response))))))
