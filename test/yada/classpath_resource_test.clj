;; Copyright © 2014-2017, JUXT LTD.

(ns yada.classpath-resource-test
  (:require
   [clojure.test :refer :all]
   [schema.test :as st]
   [yada.yada :as yada]
   [yada.resources.classpath-resource :refer [new-classpath-resource]]))

(defn get-path [resource path]
  (yada/response-for ["" resource]
                     :get
                     path))

(st/deftest classpath-resource-test
  (testing "File from classpath is served"
    (let [resource (new-classpath-resource "static")
          response (get-path resource "/test/file1.txt")
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= body "File 1\n"))))
  (testing "Index file is served"
    (let [resource (new-classpath-resource
                    "static"
                    {:index-files
                     ["file3.txt" "file1.txt" "file2.txt"]})
          response (get-path resource "/test/")
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= body "File 1\n"))))
  ;; I'm not sure if this a feature or a bug
  (testing "Directory from classpath is served"
    (let [resource (new-classpath-resource "static")
          response (get-path resource "/test")
          body (:body response)]
      (is (= 200 (:status response)))
      (is (= "file1.txt\nfile2.txt\n" body))))
  (testing "Non-existent classpath resource yields 404"
    (let [resource (new-classpath-resource "static")
          response (get-path resource "/test/file3.txt")]
      (is (= 404 (:status response))))))

;;;; Scratch

(comment
  (classpath-resource-test)
  )
