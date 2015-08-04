;; Copyright © 2015, JUXT LTD.

(ns yada.url-resource-test
  (:require [yada.resources.url-resource :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [yada.core :refer (resource)]
            [juxt.iota :refer [given]]
            [ring.mock.request :as mock]
            )
  (:import [java.io BufferedReader]))

;; Test a single Java resource. Note that this isn't a particularly useful
;; resource, because it contains no knowledge of when it was modified,
;; how big it is, etc. (unless we can infer where it came from, if jar,
;; use the file-size stored in the java.util.zip.JarEntry for
;; content-length.)

(deftest resource-test
  (let [resource (io/resource "static/css/fonts.css")
        handler (yada.core/resource resource)
        response @(handler (mock/request :get "/"))]
    (given response
      identity :? some?
      :status := 200
      [:headers "content-type"] := "text/css;charset=utf-8"
      [:headers "content-length"] :? nil?
      :body :? (partial instance? BufferedReader))))
