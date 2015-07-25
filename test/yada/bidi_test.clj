;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [byte-streams :as bs]
   [yada.yada :refer (yada) :as yada]
   ;; TODO: These resources should be loaded automatically via a yada ns
   yada.resources.string-resource
   [juxt.iota :refer (given)]
   yada.bidi
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
   {"/status" (yada "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure {"/a" (yada "Secret area A")
                          "/b" (yada "Secret area B")})}])

(deftest api-tests
  (let [h (-> api bidi/unroll-route make-handler)
        response @(h (request :get "/api/status"))]
    (testing "hello"
      (given response
        :status := 200
        :headers :> {"content-length" 12}
        [:body #(bs/convert % String)] := "API working!"))))
