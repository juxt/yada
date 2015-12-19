;; Copyright © 2015, JUXT LTD.

(ns yada.bidi-test
  (:require
   [clojure.test :refer :all]
   [clojure.walk :refer (postwalk)]
   [bidi.bidi :as bidi :refer (Matched compile-route succeed)]
   [bidi.ring :refer (make-handler Ring)]
   [byte-streams :as bs]
   [ring.mock.request :refer (request)]
   [ring.util.codec :as codec]
   yada.bidi
   [yada.walk :refer (basic-auth)]
   [yada.yada :as yada :refer [yada]]))

(defn make-api []
  ["/api"
   {"/status" (yada "API working!")
    "/hello" (fn [req] {:body "hello"})
    "/protected" (basic-auth
                  "Protected" (fn [ctx]
                                (or
                                 (when-let [auth (:authentication ctx)]
                                   (= ((juxt :user :password) auth)
                                      ["alice" "password"]))
                                 :not-authorized))
                  {"/a" (yada "Secret area A")
                   "/b" (yada "Secret area B")})}])

(deftest status
  (let [h (-> (make-api) make-handler)
        response @(h (request :get "/api/status"))]
    (testing "status"
      (is (= 200 (:status response)))
      (is (= (count (.getBytes "API working!" "UTF-8")) (get-in response [:headers "content-length"])))
      (is (= "API working!" (bs/to-string (:body response)))))))

#_(deftest secure-route
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
