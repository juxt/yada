;; Copyright © 2015, JUXT LTD.

(ns yada.string-resource-test
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clj-time.core :as time]
   [clj-time.coerce :refer (to-date)]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (format-date)]
   [yada.yada :as yada :refer [yada]]))

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
          (yada
           (merge
            (yada/as-resource resource)
            {:produces {:media-type #{"text/plain"}
                        ;; TODO: See comment above, this
                        ;; should not be necessary, somehow
                        ;; the charset should default to
                        ;; UTF-8 on strings, not sure how.
                        :charset #{"UTF-8"}}}))
          request (request :get "/")
          response @(handler request)]
      (is (= "text/plain;charset=utf-8" (get-in response [:headers "content-type"]))))

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

      (is (= 200 (:status response)))
      (is (= {"content-length" (str (count "Hello World!"))
              "content-type" "text/plain;charset=utf-8"} (select-keys (:headers response) ["content-length" "content-type"])))
      (is (instance? java.nio.ByteBuffer (:body response)))))

  (testing "if-last-modified"
    ;; We set the time to yesterday, to avoid producing dates in the future
    (time/do-at (time/minus (time/now) (time/days 1))

                (let [resource "Hello World!"
                      handler (yada resource)]

                  (let [request (assoc (request :get "/") :id 1)
                        response @(handler request)]

                    ;; First request gets a 200
                    (is (= 200 (:status response)))
                    (is (= {"content-length" (str (count "Hello World!"))} (select-keys (:headers response) ["content-length"]))))

                  (let [request (merge-with merge (request :get "/")
                                            {:headers {"if-modified-since" (format-date (to-date (time/plus (time/now) (time/hours 1))))}})
                        response @(handler request)]

                    (is (= 304 (:status response)))))))

  (testing "safe-by-default"
    (let [resource "Hello World!"
          handler (yada/yada resource)]

      (doseq [method [:put :post :delete]]
        (let [response @(handler (request method "/"))
              allow-header (get-in response [:headers "allow"])]
          (is (= 405 (:status response)))
          (is (not (nil? allow-header)))
          (is (= #{"GET" "HEAD" "OPTIONS"} (parse-allow (get-in response [:headers "allow"])))))))))
