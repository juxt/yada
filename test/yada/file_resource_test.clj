;; Copyright © 2015, JUXT LTD.

(ns yada.file-resource-test
  (:require [bidi.ring :refer [make-handler]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [ring.mock.request :refer [request]]
            [ring.util.time :refer [parse-date format-date]]
            [yada.bidi :as yb]
            [yada.core :refer [yada]]
            [yada.file-resource :refer :all]
            [yada.resource :as yst]
            [yada.test.util :refer [given]])
  (:import [java.io File ByteArrayInputStream]
           [java.util Date]))

(def exists? (memfn exists))

(deftest legal-name-test
  (are [x] (not (#'legal-name x))
    ".." "./foo" "./../home" "./.." "/foo" "a/b")
  (are [x] (legal-name x)
    "a" "b.txt" "c..txt"))

;; Test an actual file resource
(deftest file-test
  (let [resource (io/file "test/yada/state/test.txt")
        handler (yada resource)
        request (request :get "/")
        response @(handler request)]

    (testing "expectations of set-up"
      (given resource
        identity :? exists?
        (memfn getName) := "test.txt"))

    (testing "response"
      (given response
        identity :? some?
        :status := 200
        [:headers "content-type"] := "text/plain"
        [:body type] := File
        [:headers "content-length"] := (.length resource)))

    (testing "last-modified"
      (given response
        [:headers "last-modified" parse-date] := (java.util.Date. (.lastModified resource))
        [:headers "last-modified" parse-date (memfn getTime)] := (.lastModified resource)))

    (testing "conditional-response"
      (given @(handler (assoc-in request [:headers "if-modified-since"]
                                 (format-date (Date. (.lastModified resource)))))
        :status := 304))))

(deftest temp-file-test
  (testing "creation of a new file"
    (let [f (java.io.File/createTempFile "yada" nil nil)]
      (io/delete-file f)
      (is (not (exists? f)))

      (let [options {:methods #{:get :head :put :delete}}
            newstate {:username "alice" :name "Alice"}]

        (is (not (yst/exists? f)))

        ;; A PUT request arrives on a new URL, containing a
        ;; representation which is parsed into the following model :-
        (letfn [(make-put []
                  (merge (request :put "/" )
                         {:headers {"x-yada-debug" "true"}}
                         {:body (ByteArrayInputStream. (.getBytes (pr-str newstate)))}))]

          ;; If this resource didn't allow the PUT method, we'd get a 405.
          (let [handler (yada f (update-in options [:methods] disj :put))]
            (given @(handler (make-put))
              :status := 405))

          ;; The resource allows a PUT, the server
          ;; should create the resource with the given content and
          ;; receive a 201.

          (let [handler (yada f options)]
            (given @(handler (make-put))
              :status := 201
              :body :? nil?))

          (is (= (edn/read-string (slurp f)) newstate)
              "The file content after the PUT was not the same as that
                in the request")

          (let [handler (yada f options)]
            (given @(handler (request :get "/"))
              :status := 200
              [:body slurp edn/read-string] := newstate)

            ;; Update the resource, since it already exists, we get a 204
            ;; TODO Check spec, is this the correct status code?

            (given @(handler (make-put))
              :status := 204)

            (given @(handler (request :delete "/"))
              :status := 204)

            (given @(handler (request :get "/"))
              :status := 404
              :body :? nil?))

          (is (not (.exists f)) "File should have been deleted by the DELETE"))))))

(deftest temp-dir-test
  (let [f (java.io.File/createTempFile "yada" nil nil)]
    (io/delete-file f)
    (is (not (exists? f)))
    (.mkdirs f)
    (is (exists? f))

    (let [resource f
          options {:methods #{:get :head :put :delete}}
          handler (yb/resource resource options)
          root-handler (make-handler ["/" handler])]

      (testing "Start with 0 files"
        (given f
          identity :? yst/exists?
          [(memfn listFiles) count] := 0))

      (testing "PUT a new file"
        (given @(root-handler
                 (merge (request :put "/abc.txt")
                        {:body (ByteArrayInputStream. (.getBytes "foo"))}))
          :status := 204))

      (given f
        [(memfn listFiles) count] := 1)

      (testing "GET the new file"
        (given @(root-handler (request :get "/abc.txt"))
          :status := 200
          [:body slurp] := "foo"))

      (testing "PUT another file"
        (given @(root-handler
                 (merge (request :put "/def.txt")
                        {:body (ByteArrayInputStream. (.getBytes "bar"))}))
          :status := 204))

      (testing "GET the index"
        (given @(root-handler (request :get "/"))
          :status := 200
          [:body] := "abc.txt\ndef.txt"
          [:headers "content-type"] := "text/html"))

      (testing "GET the file that doesn't exist"
        (given @(root-handler (request :get "/abcd.txt"))
          :status := 404))

      (testing "DELETE the new files"
        (given @(root-handler (request :delete "/abc.txt"))
          :status := 204))

      (given f
        [(memfn listFiles) count] := 1)

      (given @(root-handler (request :delete "/def.txt"))
        :status := 204)

      (given f
        [(memfn listFiles) count] := 0)

      (testing "DELETE the directory"
          (given @(root-handler (request :delete "/"))
            :status := 204))

      (given f
        identity :!? yst/exists?))))
