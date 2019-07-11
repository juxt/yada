(ns yada.authorization-test
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [manifold.deferred :as d]
   [ring.mock.request :as mock]
   [yada.authentication :as ya]
   [yada.authentication.http-basic :as basic]
   [yada.authentication.http-bearer :as bearer]
   [yada.context :as ctx]
   [yada.security :as sec]
   [yada.authentication-test :refer [encode-basic-authorization-token]]
   [yada.syntax :as syn]
   [yada.yada :as yada]))

(deftest resource-test
  (let [resource
        {:methods {:get {:produces {:media-type "application/edn"}
                         :response (fn [ctx] "OK")}}
         ::ya/authenticators
         [(basic/http-basic-authenticator
           (fn [ctx user password attributes]
             (when (= [user password] ["alice" "wonderland"])
               {:user "Alice"}))
           {"realm" "WallyWorld"})]}]

    (testing "correct authentication"
      (let [ctx (-> (ctx/make-context)
                    (assoc :resource resource)
                    (assoc :request (->
                                     (mock/request :get "/")
                                     (mock/header "authorization" (str "Basic " (encode-basic-authorization-token "alice" "wonderland"))))))
            ctx @(d/chain
                  ctx
                  sec/authenticate
                  sec/authorize)
            authentication (first (::ya/authentications ctx))]
        (is (= 1 (count (::ya/authentications ctx))))
        (is (= "Basic" (::ya/scheme authentication)))
        (is (= "Alice" (:user authentication)))))

    (testing "missing authentication produces challenge"
      (let [ctx (-> (ctx/make-context)
                    (assoc :resource resource)
                    (assoc :request (->
                                     (mock/request :get "/"))))
            ctx @(sec/authenticate ctx)]
        (is (empty? (::ya/authentications ctx)))
        (is (= "Basic charset=\"UTF-8\", realm=\"WallyWorld\""
               (get-in ctx [:response :headers "www-authenticate"])))))

    (testing "incorrect authentication produces challenge"
      (let [ctx (-> (ctx/make-context)
                    (assoc :resource resource)
                    (assoc :request (->
                                     (mock/request :get "/")
                                     (mock/header "authorization" (str "Basic " (encode-basic-authorization-token "alice" "letmein"))))))
            ctx @(sec/authenticate ctx)]
        (is (empty? (::ya/authentications ctx)))
        (is (= "Basic charset=\"UTF-8\", realm=\"WallyWorld\""
               (get-in ctx [:response :headers "www-authenticate"])))))))
