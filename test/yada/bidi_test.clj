;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [ring.util.codec :as codec]
   [byte-streams :as bs]
   [yada.yada :as yada]
   ;; TODO: These resources should be loaded automatically via a yada ns
   yada.resources.string-resource
   [juxt.iota :refer (given)]
   [yada.bidi :refer (secure-with)]
   [bidi.bidi :as bidi :refer (Matched compile-route succeed)]
   [bidi.ring :refer (make-handler Ring)]
   [ring.mock.request :refer (request)]
   [clojure.walk :refer (postwalk)]
   bidi.bidi))

(defn make-api []
  ["/api"
   {"/status" (yada/resource "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (secure-with
                  {:security {:type :basic :realm "Protected"}
                   :authorization (fn [ctx]
                                    (or
                                     (when-let [auth (:authentication ctx)]
                                       (= ((juxt :user :password) auth)
                                          ["alice" "password"]))
                                     :not-authorized))}
                  {"/a" (yada/resource "Secret area A")
                   "/b" (yada/resource "Secret area B")})}])

(deftest status
  (let [h (-> (make-api) make-handler)
        response @(h (request :get "/api/status"))]
    (testing "status"
      (given response
        :status := 200
        :headers :> {"content-length" (count (.getBytes "API working!" "UTF-8"))}
        [:body #(bs/convert % String)] := "API working!"))))

(deftest secure-route
  (let [h (-> (make-api) make-handler)]
    (testing "without-credentials"
      (let [response @(h (request :get "/api/protected/a"))]
        (given response
          :status := 401                ; Unauthorized
          :headers :> {"www-authenticate" "Basic realm=\"Protected\""}
          :body :? nil?)))
    (testing "with-credentials"
      (let [response @(h (merge-with
                          merge
                          (request :get "/api/protected/a")
                          {:headers {"authorization" (str "Basic " (codec/base64-encode (.getBytes "alice:password")))}}))]
        (given response
          :status := 200                ; OK
          [:body #(bs/convert % String)] := "Secret area A"
          )))))
