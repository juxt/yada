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
   [yada.yada :as yada :refer [yada]]
   [yada.protocols :as p]
   [yada.resources.file-resource :refer :all]
   [yada.test.util :refer [to-string]])
  (:import
   [java.io File ByteArrayInputStream]
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
      (is (exists? resource))
      (is (= "test.txt" (.getName resource))))

    (testing "response"
      (is (some? response))
      (is (= 200 (:status response)))
      (is (= "text/plain" (get-in response [:headers "content-type"])))
      (is (instance? File (:body response)))
      (is (= (.length resource) (get-in response [:headers "content-length"]))))

    (testing "last-modified"
      (let [d (get-in response [:headers "last-modified"])]
        (is (= (java.util.Date. (.lastModified resource)) (parse-date d)))))

    (testing "conditional-response"
      (let [r @(handler (assoc-in request [:headers "if-modified-since"]
                                  (format-date (Date. (.lastModified resource)))))]
        (is (= 304 (:status r)))))))

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
          (let [handler (yada (update (yada/as-resource f) :methods dissoc :put))
                r @(handler (make-put))]
            (is (= 405 (:status r))))

          ;; The resource allows a PUT, the server
          ;; should create the resource with the given content and
          ;; receive a 201.

          (let [handler (yada f)
                r @(handler (make-put))]
            (is (= 201 (:status r)))
            (is (nil? (:body r))))

          (is (= (edn/read-string (slurp f)) newstate)
              "The file content after the PUT was not the same as that
                in the request")

          (let [handler (yada f)
                r @(handler (request :get "/"))]
            (is (= 200 (:status r)))
            (is (= "application/edn" (get-in r [:headers "content-type"])))
            (is (= newstate (edn/read-string (slurp (:body r)))))

            ;; Update the resource, since it already exists, we get a 204
            ;; TODO Check spec, is this the correct status code?

            (is (= 204 (:status @(handler (make-put)))))

            (is (exists? f))
            (is (= 204 (:status @(handler (request :delete "/")))))

            (is (not (exists? f)))

            (is (= 404 (:status @(handler (request :get "/"))))))

          (is (not (.exists f)) "File should have been deleted by the DELETE"))))))

#_(st/deftest dir-index-test
  (let [resource (yada (io/file "talks"))
        h (br/make-handler ["/" resource])
        req (request :get "/")
        resp @(h req)]
    (is (nil? resp))
    (clojure.pprint/pprint (to-string (:body resp)))
    (is (nil? (to-string (:body resp))))))

