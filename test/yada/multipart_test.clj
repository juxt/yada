;; Copyright © 2015, JUXT LTD.

(ns yada.multipart-test
  (:require
   [clj-index.core :refer [bm-index match]]
   [byte-streams :as b]
   [juxt.iota :refer [given]]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.media-type :as mt]
   [yada.multipart :refer [create-matcher]]))

(deftest parse-content-type-header
  (let [content-type "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"]
    (given (mt/string->media-type content-type)
      :name := "multipart/form-data"
      :type := "multipart"
      :subtype := "form-data"
      [:parameters "boundary"] := "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")))

(deftest boundary-search-test
  (is
   (=
    ((create-matcher "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")
     (io/input-stream (io/resource "yada/multipart-1")))
    '(2 97 193 285))))
