(ns yada.string-resource-test
  (:require
   [clojure.string :as str]
   [clj-time.core :as time]
   [clj-time.coerce :refer (to-date)]
   [ring.util.time :refer (format-date)]
   yada.resources.string-resource
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [yada.core :refer [yada]]
   [ring.mock.request :refer [request]]
   [juxt.iota :refer [given]]))


(deftest string-test
  (testing "Producing a Java string implies utf-8 charset"
    ;; Yada should make life easy for developers. If the developer does not
    ;; declare a charset parameter, it is possible to infer one based on the
    ;; resource. For files, this is not possible, since there is no reliable
    ;; or performant way of determining the encoding used for a file's
    ;; content. However, with java.lang.String instances, this isn't the
    ;; case. We know the Java platform stores Strings as Unicode utf-16, and
    ;; that it can output these strings in utf-8.
    (let [resource "Hello World"
          handler
          (yada resource
                :representations [{:content-type #{"text/plain"}
                                   ;; TODO: See comment above, this
                                   ;; should not be necessary, somehow
                                   ;; the charset should default to
                                   ;; UTF-8 on strings, not sure how.
                                   :charset #{"UTF-8"}}])
          request (request :get "/")
          response @(handler request)]
      (given response
        [:headers "content-type"] := "text/plain;charset=utf-8"))

    ;; TODO: If strings are used, then an explicit charset provided in
    ;; the :produces entry should be honored and used when writing the
    ;; String.
    ))

(defn parse-allow [s]
  (is s)
  (when s
    (set (str/split s #"\s*,\s*"))))

(deftest hello-world-test
  (testing "hello-world"
    (let [resource "Hello World!"
          handler (yada resource)
          request (request :get "/")
          response @(handler request)]

      (given response
        :status := 200
        :headers :> {"content-length" (count "Hello World!")
                     "content-type" "text/plain;charset=utf-8"}
        :body :? (partial instance? java.nio.ByteBuffer))))

  (testing "if-last-modified"
    (time/do-at (time/minus (time/now) (time/days 6))
      (let [resource "Hello World!"
            handler (yada resource)]

        (time/do-at (time/minus (time/now) (time/days 3))

          (let [request (request :get "/")
                response @(handler request)]

            (given response
              :status := 200
              :headers :> {"content-length" (count "Hello World!")}
              :body :? (partial instance? java.nio.ByteBuffer))))

        (time/do-at (time/minus (time/now) (time/days 2))

          (let [request (merge-with merge (request :get "/")
                                    {:headers {"if-modified-since" (format-date (to-date (time/minus (time/now) (time/days 2))))}})
                response @(handler request)]

            (given response
              :status := 304))))))

  (testing "safe-by-default"
      (let [resource "Hello World!"
            handler (yada resource)]

        (doseq [method [:put :post :delete]]
          (given @(handler (request method "/"))
            :status := 405
            [:headers "allow"] :!? nil?
            [:headers "allow" parse-allow] := #{"GET" "HEAD"}
            ))))

  #_(testing "wrap-in-atom"
      (let [resource (atom "Hello World!")
            handler (yada resource)]

        (given @(handler (request :put "/"))
          :status := 200
          ))))
