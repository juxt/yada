;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :refer (yada) :as yada]
   [yada.test.util :refer (given)]
   [yada.bidi :refer (resource-leaf)]
   [bidi.bidi :as bidi :refer (Matched compile-route succeed context)]
   [bidi.ring :refer (make-handler Ring)]
   [ring.mock.request :refer (request)]))

(def security
  {:security {:type :basic :realm "Protected"}
   :authorization (fn [ctx]
                    (or
                     (when-let [auth (:authentication ctx)]
                       (= ((juxt :user :password) auth)
                          ["alice" "password"]))
                     :not-authorized))})

(defn secure [routes]
  (yada.bidi/partial security routes))

(def api
  ["/api"
   {"/status" (resource-leaf "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure {"/a" (resource-leaf "Secret area A")
                          "/b" (resource-leaf "Secret area B")})}])

(deftest api-tests
  (let [h (make-handler api)
        response @(h (request :get "/api/status"))]
    (testing "hello"
      (given response
        :status := 200
        :headers :> {"content-length" 12}
        :body := "API working!"))))
