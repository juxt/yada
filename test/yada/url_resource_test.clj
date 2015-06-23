(ns yada.url-resource-test
  (:require [yada.url-resource :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [yada.core :refer (yada)]
            [yada.test.util :refer [given]]
            [ring.mock.request :as mock]
            )
  (:import [java.io BufferedInputStream]))

;; Test a single Java resource. Note that this isn't a particularly useful
;; resource, because it contains no knowledge of when it was modified,
;; how big it is, etc. (unless we can infer where it came from, if jar,
;; use the file-size stored in the java.util.zip.JarEntry for
;; content-length.)

(deftest resource-test
  (let [resource (io/resource "public/css/fonts.css")
        handler (yada resource)
        response @(handler (mock/request :get "/"))]
    (given response
      identity :? some?
      :status := 200
      [:headers "content-type"] := "text/css"
      [:headers "content-length"] :? nil?
      :body :? (partial instance? BufferedInputStream))))
