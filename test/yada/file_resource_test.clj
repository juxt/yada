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
   [bidi.bidi :as bidi]
   [bidi.ring :as br]
   yada.bidi
   [yada.yada :as yada :refer [yada]]
   [yada.protocols :as p]
   [yada.resources.file-resource :refer :all]
   [yada.test.util :refer [to-string]]
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

(st/deftest temp-file-test
  (testing "creation of a new file"
    (let [f (java.io.File/createTempFile "yada" ".edn" nil)]
      (io/delete-file f)
      (is (not (exists? f)))

      (let [newstate {:username "alice" :name "Alice"}]

        ;; A PUT request arrives on a new URL, containing a
        ;; representation which is parsed into the following model :-
        (letfn [(make-put []
                  (merge (request :put "/" )
                         {:headers {"x-yada-debug" "true"}}
                         {:body (ByteArrayInputStream. (.getBytes (pr-str newstate)))}))]
          ;; If this resource didn't allow the PUT method, we'd get a 405.
          (let [handler (yada (update (yada/as-resource f) :methods dissoc :put))]
            (given @(handler (make-put))
                   :status := 405))

          ;; The resource allows a PUT, the server
          ;; should create the resource with the given content and
          ;; receive a 201.

          (let [handler (yada f)]
            (given @(handler (make-put))
                   :status := 201
                   :body :? nil?))

          (is (= (edn/read-string (slurp f)) newstate)
              "The file content after the PUT was not the same as that
                in the request")

          (let [handler (yada f)]
            (given @(handler (request :get "/"))
                   :status := 200
                   [:headers "content-type"] := "application/edn"
                   [:body slurp edn/read-string] := newstate
                   )

            ;; Update the resource, since it already exists, we get a 204
            ;; TODO Check spec, is this the correct status code?

            (given @(handler (make-put))
                   :status := 204)

            (is (exists? f))

            (given @(handler (request :delete "/"))
                     :status := 204)

            (is (not (exists? f)))

            (given @(handler (request :get "/"))
                     :status := 404))

          (is (not (.exists f)) "File should have been deleted by the DELETE"))))))

#_(st/deftest dir-index-test
  (let [resource (yada (io/file "talks"))
        h (br/make-handler ["/" resource])
        req (request :get "/")
        resp @(h req)]
    (is (nil? resp))
    (clojure.pprint/pprint (to-string (:body resp)))
    (is (nil? (to-string (:body resp))))))

