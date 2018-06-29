;; Copyright © 2014-2017, JUXT LTD.

(ns yada.file-resource-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer [format-date parse-date]]
   [schema.test :as st]
   [yada.handler :refer [handler]]
   [yada.resource :refer [as-resource]])
  (:import [java.io ByteArrayInputStream File]
           java.util.Date))

(defn exists? [x]
  (.exists ^java.io.File x))

#_(deftest legal-name-test
    (are [x] (not (#'legal-name x))
      ".." "./foo" "./../home" "./.." "/foo" "a/b")
    (are [x] (legal-name x)
      "a" "b.txt" "c..txt"))

;; Test an actual file resource
(st/deftest file-test
  (let [resource (io/file (System/getProperty "yada.dir") "test/yada/state/test.txt")
        h (handler resource)
        request (request :get "/")
        response @(h request)
        last-modified-second-precision (parse-date (format-date (java.util.Date. (.lastModified resource))))]

    (testing "expectations of set-up"
      (is (exists? resource))
      (is (= "test.txt" (.getName resource))))

    (testing "response"
      (is (some? response))
      (is (= 200 (:status response)))
      (is (= "text/plain" (get-in response [:headers "content-type"])))
      (is (instance? File (:body response)))
      (is (= (str (.length resource)) (get-in response [:headers "content-length"]))))

    (testing "last-modified"
      (let [d (get-in response [:headers "last-modified"])]
        (is (= last-modified-second-precision (parse-date d)))))

    (testing "conditional-response"
      (let [r @(h (assoc-in request [:headers "if-modified-since"]
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
          (let [h (handler (update (as-resource f) :methods dissoc :put))
                r @(h (make-put))]
            (is (= 405 (:status r))))

          ;; The resource allows a PUT, the server
          ;; should create the resource with the given content and
          ;; receive a 201.

          (let [h (handler f)
                r @(h (make-put))]
            (is (= 201 (:status r)))
            (is (nil? (:body r))))

          (is (= (edn/read-string (slurp f)) newstate)
              "The file content after the PUT was not the same as that
                in the request")

          (let [h (handler f)
                r @(h (request :get "/"))]
            (is (= 200 (:status r)))
            (is (= "application/edn" (get-in r [:headers "content-type"])))
            (is (= newstate (edn/read-string (slurp (:body r)))))

            ;; Update the resource, since it already exists, we get a 204
            ;; TODO Check spec, is this the correct status code?

            (is (= 204 (:status @(h (make-put)))))

            (is (exists? f))
            (is (= 204 (:status @(h (request :delete "/")))))

            (is (not (exists? f)))

            (is (= 404 (:status @(h (request :get "/"))))))

          (is (not (.exists f)) "File should have been deleted by the DELETE"))))))

#_(st/deftest dir-index-test
  (let [resource (yada (io/file "talks"))
        h (br/make-handler ["/" resource])
        req (request :get "/")
        resp @(h req)]
    (is (nil? resp))
    (clojure.pprint/pprint (to-string (:body resp)))
    (is (nil? (to-string (:body resp))))))
