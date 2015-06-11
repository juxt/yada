;; Copyright © 2015, JUXT LTD.

(ns yada.resource-test
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [yada.core :refer [yada]]
   [yada.bidi :as yb]
   [yada.resource :as yst :refer (legal-name)]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (parse-date format-date)]
   [yada.test.util :refer (given)]
   [bidi.bidi :refer (Matched)]
   [bidi.ring :refer (make-handler)])
  (:import [java.util Date]
           [java.io File BufferedInputStream ByteArrayInputStream]))

(def exists? (memfn exists))

(deftest legal-name-test
  (are [x] (not (legal-name x))
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


;; TODO Finish the above, then start serving whole directories.

;; Test a single resource. Note that this isn't a particularly useful
;; resource, because it contains no knowledge of when it was modified,
;; how big it is, etc. (unless we can infer where it came from, if jar,
;; use the file-size stored in the java.util.zip.JarEntry for
;; content-length.)

#_(deftest resource-test
  (let [resource {:state (io/resource "public/css/fonts.css")}
        handler (yada resource)
        response @(handler (request :get "/"))]
    (is (some? response))
    (is (= (:status response) 200))
    (is (= (get-in response [:headers "content-type"]) "text/css"))
    (is (instance? BufferedInputStream (:body response)))))

;; TODO: Test conditional content
;; TODO: Serve up file images and other content required for the examples with yada - don't use bidi/resources
;; TODO: yada should replace bidi's resources, files, etc.  and do a far better job
;; TODO: Observation: wrap-not-modified works on the response only - i.e. it still causes the handler to handle the request (and do work) on the server.

;; A resource does not map directly onto a database table.
;; But it can, often, map onto a document in a NoSQL datastore

;; Schema is the defining aspect of a resource's model.
;; Unlike XML Schema, it is not concerned with representation, only the model

;; ---------------------------------------------------------------

;; State is sometimes real, like a file, sometimes merely conceptual.
;; State of the latter variety needs support

;; We define state with schema, which shapes the state but defining its constraints and boundaries.

;; Protocols are the answer here
;;

;; Loadable - state can be loaded
;; Storable

;; A Clojure record represents the state

;; In another namespace, e.g. kv-store, we extend the State type with Loadable and Saveable

#_(defprotocol Storage
  (store-state! [_ s] "Put the state into storage")
  (fetch-state [_] "Get the state from storage"))

;; A file defines both its state and its storage
#_(extend-type File
  Storage
  (store-state! [f s] (println "Writing to f new contents s"))
  (fetch-state [f] f))

;; Work on java.io.File first, then clojure.lang.Atom, then integrate schema-enforced maps


;; I suppose a file can also be a sql-lite database file, backing a resource. It should be OK to have a sql-lite file per resource set, e.g. /customers /customers/1234 - so could be set in a yada/partial

#_(defrecord StoredState []
  Storage
  (store-state! [_ state] (println "Putting state" state))

  ;; Should support range requests, query parameters, partial query parameters?
  (fetch-state [_] (println "Getting state") {:username "bob" :name "Bob"})
  )

#_(let [resource-map
      {:state (StoredState.)}]

  ;; A PUT request arrives on a new URL, containing a representation which is parsed into the following model :-
    (let [state {:username "alice" :name "Alice"}]
    ;; which is then stored
    (store-state! (:state resource-map) state)))


;; State is always at rest, when state is in-flight, it exists merely as a representation
;; A clojure map is a _representation_, albeit a flexible one with respect to serialization (whereas an atom containing the clojure map would be _state_)

;; Design goal: the ability to have a file returned to aleph intact
;; Design goal: the ability to do this: (yada file), (yada dir),
;; (yada "target")
;; (yada ".") would mean serve working directory
;; and yada looks like a file-server

;; get away from resource-maps as being the only way to call yada, but always have the option to convert between them (user generates a resource map and converts to a yada handler, or constructs a yada handler and extracts a resource map)
