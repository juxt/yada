;; Copyright © 2015, JUXT LTD.

(ns yada.file-resource-test
  (:require
   [byte-streams :as bs]
   ;;[bidi.ring :refer [make-handler]]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.tools.logging :refer :all]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer [parse-date format-date]]
   [schema.test :as st]
   ;;[yada.bidi :as yb]
   [yada.yada :as yada]
   [yada.protocols :as p]
   [yada.resources.file-resource :refer :all]
   [juxt.iota :refer [given]])
  (:import [java.io File ByteArrayInputStream]
           [java.util Date]))

(def exists? (memfn exists))

#_(deftest legal-name-test
  (are [x] (not (#'legal-name x))
    ".." "./foo" "./../home" "./.." "/foo" "a/b")
  (are [x] (legal-name x)
    "a" "b.txt" "c..txt"))

;; Test an actual file resource
(st/deftest file-test
  (let [resource (io/file "test/yada/state/test.txt")
        handler (yada/resource resource)
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

(st/deftest temp-file-test
  (testing "creation of a new file"
    (let [f (java.io.File/createTempFile "yada" ".edn" nil)]
      (io/delete-file f)
      (is (not (exists? f)))

      (let [options {:allowed-methods #{:get :head :put :delete}}
            newstate {:username "alice" :name "Alice"}]

        ;; A PUT request arrives on a new URL, containing a
        ;; representation which is parsed into the following model :-
        (letfn [(make-put []
                  (merge (request :put "/" )
                         {:headers {"x-yada-debug" "true"}}
                         {:body (ByteArrayInputStream. (.getBytes (pr-str newstate)))}))]

          ;; If this resource didn't allow the PUT method, we'd get a 405.
          (let [handler (yada/resource f (update-in options [:allowed-methods] disj :put))]
            (given @(handler (make-put))
              :status := 405))

          ;; The resource allows a PUT, the server
          ;; should create the resource with the given content and
          ;; receive a 201.

          (let [handler (yada/resource f options)]
            (given @(handler (make-put))
              :status := 201
              :body :? nil?))

          (is (= (edn/read-string (slurp f)) newstate)
              "The file content after the PUT was not the same as that
                in the request")

          (let [handler (yada/resource f options)]
            (given @(handler (request :get "/"))
              :status := 200
              [:headers "content-type"] := "application/edn"
              [:body slurp edn/read-string] := newstate)

            ;; Update the resource, since it already exists, we get a 204
            ;; TODO Check spec, is this the correct status code?

            (given @(handler (make-put))
              :status := 204)

            (is (exists? f))

            (given @(handler (request :delete "/"))
              :status := 204)

            (is (not (exists? f)))

            (given @(handler (request :get "/"))
              :status := 404
              :body :? nil?))

          (is (not (.exists f)) "File should have been deleted by the DELETE"))))))

#_(st/deftest temp-dir-test
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory "yada" (make-array java.nio.file.attribute.FileAttribute 0)))]
    (is (exists? dir))

    (let [handler (yada/resource dir {:methods #{:get :head :put :delete}})]

      (testing "Start with 0 files"
        (given dir
          [p/make-resource #(p/exists? % {})] :? true?
          [(memfn listFiles) count] := 0))

      (testing "PUT a new file"
        (given @(handler
                 (merge (request :put "/")
                        {:path-info "abc.txt"
                         :body (ByteArrayInputStream. (.getBytes "foo"))}))
          :status := 204))

      (given dir
        [(memfn listFiles) count] := 1)

      (testing "GET the new file"
        (given @(handler (merge
                          (request :get "/")
                          {:path-info "abc.txt"}))
          :status := 200
          [:body slurp] := "foo"))

      (testing "PUT another file"
        (given @(handler
                 (merge (request :put "/")
                        {:path-info "håkan.txt"
                         :body (ByteArrayInputStream. (.getBytes "bar"))}))
          :status := 204))

      (testing "GET the index, in US-ASCII"
        (given @(handler
                 (merge-with merge
                             (request :get "/")
                             {:path-info ""
                              :headers {"accept" "text/plain"
                                        "accept-charset" "US-ASCII"}}))
          :status := 200
          [:headers "content-type"] := "text/plain;charset=us-ascii"
          [:body #(bs/convert % String)] := "abc.txt\nh?kan.txt\n"))

      ;; In ASCII, Håkan's 'å' gets turned into a '?', so let's try UTF-8 (the default)
      (testing "GET the index"
        (given @(handler (merge-with merge
                                     (request :get "/")
                                     {:path-info ""
                                      :headers {"accept" "text/plain"}}))
          :status := 200
          [:headers "content-type"] := "text/plain;charset=utf-8"
          [:body #(bs/convert % String)] := "abc.txt\nhåkan.txt\n"))

      (testing "GET the file that doesn't exist"
        (given @(handler (merge (request :get "/")
                                {:path-info "abcd.txt"}))
          :status := 404))

      (testing "DELETE the new files"
        (given @(handler (merge (request :delete "/")
                                {:path-info "abc.txt"}))
          :status := 204))

      (given dir
        [(memfn listFiles) count] := 1)

      (given @(handler (merge (request :delete "/")
                              {:path-info "håkan.txt"}))
        :status := 204)

      (given dir
        [(memfn listFiles) count] := 0)

      (testing "DELETE the directory"
        (given @(handler (merge
                          (request :delete "/")
                          {:path-info ""}))
          :status := 204))

      (given dir
        identity :!? exists?))))
