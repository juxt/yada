;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :refer (yada) :as yada]
   [yada.bidi :refer (resource)]
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
   {"/status" (resource "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure {"/a" (resource :body "Secret area A")
                          "/b" (resource :body "Secret area B")})}])

(deftest api-tests
  (let [h (make-handler api)
        send (comp (juxt :status :headers :body) deref h)]
    (testing "hello"
      (is (= (send (request :get "/api/status"))
             [200 nil "API working!"])))))
