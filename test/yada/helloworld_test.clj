;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test the Hello World tutorial"}
  yada.helloworld-test
  (:require
   [clojure.test :refer :all]
   [cheshire.core :as json]
   [byte-streams :as bs]
   [juxt.iota :refer (given)]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (parse-date format-date)]
   [yada.dev.hello :as hello]
   [yada.media-type :as mt]
   [yada.util :refer (parse-csv)]
   [yada.test.util :refer (etag? to-string)]
   [yada.yada :as yada]))

(defn validate-headers? [headers]
  (->>
   (for [[k v] headers]
     (case k
       "content-length" (when-not (and (number? v) (not (neg? v)) ) "content-length not a non-negative number")
       "content-type" (when-not (mt/string->media-type v) "mime-type not valid")
       "last-modified" (when-not (instance? java.util.Date (parse-date v)) "last-modified not a date")
       "vary" (when-not (pos? (count (parse-csv v))) "vary empty")
       "allow" (when-not (pos? (count (parse-csv v))) "allow empty")
       "etag" (when-not (etag? v) "not a valid etag")
       (throw (ex-info "Cannot validate unrecognized header" {:k k :v v}))))
   (remove nil?) vec))

(deftest string-test
  (let [resource (hello/hello)]
    (given @(resource (request :get "/"))
      :status := 200
      [:headers keys set] := #{"last-modified" "content-type" "content-length" "vary" "etag"}
      [:headers validate-headers?] := []
      [:headers "content-type"] := "text/plain;charset=utf-8"
      [:headers "content-length"] := 13
      [:headers "vary" parse-csv set] := #{"accept-charset"}
      [:headers "etag"] := "67591358"
      [:body to-string] := "Hello World!\n")))

(deftest swagger-intro-test
  (let [resource (hello/hello-api)
        response @(resource (request :get "/swagger.json"))]
    (given response
      :status := 200
      [:headers keys set] := #{"last-modified" "content-type" "content-length" "vary" "etag"}
      [:headers "content-type"] := "application/json"
      [:headers "content-length"] := 421
      [:headers "vary" parse-csv set] := #{"accept-charset"}
      [:headers "etag"] := "173524339"
      )
    (given (-> response :body to-string json/decode)
      ["swagger"] := "2.0"
      ["info" "title"] := "Hello World!"
      ["info" "version"] := "1.0"
      ["info" "description"] := "Demonstrating yada + swagger"
      ["paths" "/hello" "get" "produces"] := ["text/plain"]
      )))

;; TODO: conditional request

(deftest put-not-allowed-test
  (let [resource (hello/hello)]
    (given @(resource (request :put "/"))
      :status := 405
      [:headers keys set] := #{"allow"}
      [:headers validate-headers?] := []
      [:headers "allow" parse-csv set] := #{"OPTIONS" "GET" "HEAD"}
      :body := nil)))

(deftest options-test
  (let [resource (hello/hello)]
    (given @(resource (request :options "/"))
      :status := 200
      [:headers keys set] := #{"allow"}
      [:headers validate-headers?] := []
      [:headers "allow" parse-csv set] := #{"OPTIONS" "GET" "HEAD"}
      :body := nil)))

(deftest atom-options-test
  (let [resource (hello/hello-atom)]
    (given @(resource (request :options "/"))
      :status := 200
      [:headers keys set] := #{"allow"}
      [:headers validate-headers?] := []
      [:headers "allow" parse-csv set] := #{"OPTIONS" "GET" "HEAD" "PUT" "POST" "DELETE"}
      :body := nil)))

#_(deftest put-test
  (let [resource (hello/hello-atom)]
    (given @(resource (merge (request :put "/")
                             {:body "Hello Dolly!"}))
      ;; What should a PUT return in this case?
      :status := 204
      [:headers keys set] := #{}
      [:headers validate-headers?] := []
      :body := nil)))

;; TODO: head request

#_(deftest parameters-test
  (let [resource hello/hello-parameters]
    (given @(resource (request :get "/?p=Ken"))
      :status := 200
      ;; TODO: Test a lot more, like content-type
      [:body to-string] := "Hello Ken!\n")))

;; TODO: content negotiation

#_(deftest content-negotiation-test
  (let [resource tutorial/hello-languages]
    (testing "default is Simplified Chinese"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")))
        :status := 200
        [:headers "content-language"] := "zh-ch"
        ;; TODO: Test a lot more, like content-type
        [:body to-string] := "你好世界!\n"))

    (testing "English is available on request"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")
                            (assoc-in [:headers "accept-language"] "en")))
        :status := 200
        ;; TODO: Test a lot more, like content-type
        [:headers "content-language"] := "en"
        [:body to-string] := "Hello World!\n"))))
