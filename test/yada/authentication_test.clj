;; Copyright © 2014-2019, JUXT LTD.

(ns yada.authentication-test
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
   [yada.syntax :as syn]
   [yada.yada :as yada]))

(defn encode-basic-authorization-token [user password]
  (new String (base64/encode (.getBytes (format "%s:%s" user password)))))

;; Unit tests

(deftest authenticate-test
  (let [ctx (ctx/make-context)
        authenticator {::ya/authenticate (fn [_] {:user "Frank"})}]
    (testing "Single custom authenticator"
      (is
       (=
        [(merge authenticator {:user "Frank"})]
        @(ya/authenticate
          ctx
          [authenticator]))))

    (testing "Dynamic determination of authenticators"
      (is
       (=
        [(merge authenticator {:user "Frank"})]
        @(ya/authenticate
          ctx
          (ya/resolve-authenticators-at-request
           ctx
           (fn [_] [authenticator]))))))

    (testing "Use first authenticator returning non-nil"
      (is
       (=
        [(merge (dissoc authenticator ::ya/authenticate) {:user "Frank"})]
        (->>
         @(ya/authenticate
           ctx
           [{::ya/authenticate (fn [_] nil)}
            {::ya/authenticate (fn [_] nil)}
            authenticator])
         (map #(dissoc % ::ya/authenticate))))))


    (testing "Return values from authenticator functions can be deferred"
      (let [authenticator {::ya/authenticate (fn [_] (future {:user "Stuart"}))}]
        (is
         (=
          [(merge (dissoc authenticator ::ya/authenticate) {:user "Stuart"})]
          (->>
           @(ya/authenticate
             ctx
             [{::ya/authenticate (fn [_] (future nil))}
              authenticator])
           (map #(dissoc % ::ya/authenticate)))))))

    (testing "Return values from authenticator are non-privileged"
      (let [authenticator1 {:id 1
                            ::ya/authenticate (fn [_] {:user "Stuart"})}
            authenticator2 {:id 2
                            ::ya/authenticate (fn [_] {:role "Manager"})}]
        (is
         (=
          [(merge (dissoc authenticator1 ::ya/authenticate) {:user "Stuart"})
           (merge (dissoc authenticator2 ::ya/authenticate) {:role "Manager"})]
          (->>
           @(ya/authenticate ctx [authenticator1 authenticator2])
           (map #(dissoc % ::ya/authenticate)))))))

    (testing "Result can override authenticator entry, not other way around"
      (let [authenticator {:fruit "Apple"
                           ::ya/authenticate (fn [_] {:fruit "Banana"})}
            ]
        (is
         (=
          [(merge (dissoc authenticator ::ya/authenticate) {:fruit "Banana"})]
          (->>
           @(ya/authenticate ctx [authenticator])
           (map #(dissoc % ::ya/authenticate)))))))))

(deftest http-basic-auth-test
  (let [EVE_PASSWORD "8seha87eg"]

    (testing "match user and password"
      (let [ctx (-> (ctx/make-context)
                    (assoc :request (-> (mock/request :get "/")
                                        (mock/header
                                         "Authorization"
                                         (str "Basic " (encode-basic-authorization-token "eve" EVE_PASSWORD))))))]
        (is @(ya/authenticate
              ctx
              [(basic/http-basic-authenticator
                (fn [ctx user password attributes]
                  (and (= user "eve") (= password EVE_PASSWORD)))
                {"realm" "test"})]))))

    (testing "do not match user and wrong password"
      (let [ctx (-> (ctx/make-context)
                    (assoc :request (-> (mock/request :get "/")
                                        (mock/header
                                         "Authorization"
                                         (str "Basic " (encode-basic-authorization-token "eve" "letmein"))))))]
        (is
         (empty?
          @(ya/authenticate
            ctx
            [(basic/http-basic-authenticator
              (fn [ctx user password attributes]
                (and (= user "eve") (= password EVE_PASSWORD)))
              {"realm" "test"})]))))))

  (testing "realm passed"
    (let [ctx (-> (ctx/make-context)
                  (assoc :request (-> (mock/request :get "/")
                                      (mock/header
                                       "Authorization"
                                       (str "Basic " (encode-basic-authorization-token "eve" "letmein"))))))]
      (is
       @(ya/authenticate
         ctx
         [(basic/http-basic-authenticator
           (fn [ctx user password attributes]
             (= (get attributes "realm") "test"))
           {"realm" "test"})]))))

  (testing "nil realm produces error"
    (let [ctx (-> (ctx/make-context))]
      (is
       (thrown?
        AssertionError
        [(basic/http-basic-authenticator (constantly false) {})]))))

  (let [ctx (ctx/make-context)]
    (testing "single-realm-challenge"
      (is
       (=
        "Basic charset=\"UTF-8\", realm=\"gondor\""
        (ya/challenge-str
         ctx
         [(basic/http-basic-authenticator (constantly false) {"realm" "gondor"})]))))

    (testing "double-realm-challenge"
      (is
       (=
        "Basic charset=\"UTF-8\", realm=\"gondor\", Basic charset=\"UTF-8\", realm=\"mordor\""
        (ya/challenge-str
         ctx
         [(basic/http-basic-authenticator (constantly false) {"realm" "gondor"})
          (basic/http-basic-authenticator (constantly false) {"realm" "mordor"})]))))))

(deftest challenge-order-test
  (testing "challenge order value determines response header value"
    (let [ctx (ctx/make-context)]
      (is
       (=
        "Test2 param=\"B\", Test3 param1=\"C1\", param2=\"C2\", Test1 param=\"A\""
        (ya/challenge-str
         ctx
         [{::ya/scheme "Test1"
           ::ya/challenge (fn [ctx]
                            {:params {:param "A"}})
           ::ya/challenge-order 10}

          {::ya/scheme "Test2"
           ::ya/challenge (fn [ctx]
                            {:params {:param "B"}})
           ::ya/challenge-order 5}

          {::ya/scheme "Test3"
           ::ya/challenge (fn [ctx]
                            {:params {:param1 "C1" :param2 "C2"}})
           ::ya/challenge-order 7}])))))

  (testing "No challenge-order means end of list"
    (let [ctx (ctx/make-context)]
      (is
       (=
        "Test3 param1=\"C1\", param2=\"C2\", Test1 param=\"A\", Test2 param=\"B\""
        (ya/challenge-str
         ctx
         [{::ya/scheme "Test1"
           ::ya/challenge (fn [ctx]
                            {:params {:param "A"}})
           ::ya/challenge-order 10}

          {::ya/scheme "Test2"
           ::ya/challenge (fn [ctx]
                            {:params {:param "B"}})}

          {::ya/scheme "Test3"
           ::ya/challenge (fn [ctx]
                            {:params {:param1 "C1" :param2 "C2"}})
           ::ya/challenge-order 7}]))))))

(deftest match-authorization-header-to-authenticator
  (let [log (atom [])

        authenticators [{::ya/scheme "Test1"
                         ::ya/authenticate (fn [ctx]
                                             (swap! log conj "authenticate called for Test1")
                                             nil)}
                        {::ya/authenticate (fn [ctx]
                                             (swap! log conj "authenticate called for non-http authenticator")
                                             nil)}
                        {::ya/scheme "Test2"
                         ::ya/authenticate (fn [ctx]
                                             (swap! log conj "authenticate called for Test2")
                                             nil)}]]
    @(ya/authenticate (->
                       (ctx/make-context)
                       (assoc :request (-> (mock/request :get "/")
                                           (mock/header "Authorization" "Test1")))) authenticators)
    (is (= ["authenticate called for Test1" "authenticate called for non-http authenticator"] @log))
    (reset! log [])
    @(ya/authenticate (->
                       (ctx/make-context)
                       (assoc :request (-> (mock/request :get "/")
                                           (mock/header "Authorization" "Test2")))) authenticators)
    (is (= ["authenticate called for non-http authenticator" "authenticate called for Test2"] @log))))

(deftest bearer-token-parsing-test
  (is (= #:yada.syntax{:type :yada.syntax/credentials
                       :auth-scheme "bearer",
                       :value "mF_9.B5f-4.1JqM",
                       :value-type :yada.syntax/token68} (syn/parse-credentials "Bearer mF_9.B5f-4.1JqM"
                                                                                ))))

(deftest http-bearer-auth-test
  (let [ctx
        (->
         (ctx/make-context)
         (assoc :request (-> (mock/request :get "/")
                             (mock/header "Authorization" "Bearer mF_9.B5f-4.1JqM"))))]

    (testing "Single HTTP Bearer challenge"
      (is
       (=
        "Bearer realm=\"test\", scope=\"openid profile email\""
        (ya/challenge-str
         ctx
         [(bearer/http-bearer-authenticator
           (fn [ctx token attributes])
           {"realm" "test"
            "scope" "openid profile email"})]))))

    (testing "Basic HTTP auth appears before Bearer"
      (is
       (=
        "Basic charset=\"UTF-8\", realm=\"test2\", Bearer realm=\"test1\", scope=\"openid profile email\""
        (ya/challenge-str
         ctx
         [(bearer/http-bearer-authenticator
           (fn [ctx token attributes])
           {"realm" "test1"
            "scope" "openid profile email"})
          (basic/http-basic-authenticator
           (fn [ctx user password attributes])
           {"realm" "test2"})]))))


    (testing "Attributes passed"
      (let [authenticator
            (bearer/http-bearer-authenticator
             (fn [ctx token attributes]
               (when
                   (= attributes {"realm" "test1"
                                  "scope" "openid profile email"})
                   {:message :ok}))
             {"realm" "test1"
              "scope" "openid profile email"})]
        (is
         (=
          [(merge authenticator {:message :ok})]
          @(ya/authenticate
            ctx
            [authenticator])))))

    (testing "Bearer token passed"
      (let [authenticator
            (bearer/http-bearer-authenticator
             (fn [ctx token attributes]
               (when (= token "mF_9.B5f-4.1JqM") {:message :ok}))
             {"realm" "test1"
              "scope" "openid profile email"})]
        (is
         (=
          [(merge authenticator {:message :ok})]
          @(ya/authenticate
            ctx
            [authenticator])))))))

;; Resource test

;; Integration test

#_(deftest basic-auth-test
  (let [resource
        {:methods {:get {:produces {:media-type "application/edn"}
                         :response (fn [ctx] "OK")}}}]

    (let [resource
          (assoc
           resource
           :yada.auth/authenticators
           [(basic/http-basic-authenticator
             (fn [ctx user password attributes]
               (future
                 (when (= [user password] ["alice" "wonderland"])
                   {:user "alice"})))
             {"realm" "WallyWorld"})])]

      (testing "basic authentication challenge in response when no credentials"
        (let [response (yada/response-for
                        resource
                        :get "/"
                        {:headers {"authorization"
                                   (format "Basic %s" (encode-basic-authorization-token "alice" "wonderland"))}})]
          (is (= 200 (:status response)))
          (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                 (get-in response [:headers "www-authenticate"])))))

      #_(testing "basic authentication challenge in response when no credentials"
          (let [response (yada/response-for resource)]
            (is (= 200 (:status response)))
            (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                   (get-in response [:headers "www-authenticate"])))))

      #_(testing "no basic authentication challenge when satisfactory credentials"
          (let [response (yada/response-for
                          resource :get "/"
                          {:headers {"authorization"
                                     (format "BASIC %s" (encode-basic-authorization-token "alice" "wonderland"))}})]
            (is (= 200 (:status response)))
            (is (nil? (get-in response [:headers "www-authenticate"])))))

      #_(testing "basic authentication challenge when wrong credentials"
          (let [response (yada/response-for
                          resource :get "/"
                          {:headers {"authorization"
                                     (format "BASIC %s" (encode-basic-authorization-token "alice" "pa$$w0rd"))}})]
            (is (= 200 (:status response)))
            (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                   (get-in response [:headers "www-authenticate"])))))

      #_(testing "401 challenge due to no authorization header on a protected resource"
          (let [resource (assoc
                          resource
                          :authorize (fn [ctx creds] nil)
                          )]
            (let [response (yada/response-for resource)]
              (is (= 401 (:status response)))
              (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                     (get-in response [:headers "www-authenticate"]))))))

      #_(testing "401 challenge due to bad credentials"
          (let [resource (assoc
                          resource
                          :authorize (fn [ctx creds] nil)
                          )]
            (let [response (yada/response-for
                            resource :get "/"
                            {:headers {"authorization"
                                       (format "BASIC %s" (encode-basic-authorization-token "alice" "pa$$w0rd"))}})]
              (is (= 401 (:status response)))
              (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                     (get-in response [:headers "www-authenticate"]))))))

      #_(testing "forbidden when good credentials"
          (let [resource (assoc
                          resource
                          :authorize (fn [ctx creds] nil)
                          )]
            (let [response (yada/response-for
                            resource :get "/"
                            {:headers {"authorization"
                                       (format "BASIC %s" (encode-basic-authorization-token "alice" "wonderland"))}})]
              (is (= 403 (:status response)))
              (is (nil? (get-in response [:headers "www-authenticate"])))))))))

#_(let [resource
      {:methods {:get {:produces {:media-type "application/edn"}
                       :response (fn [ctx] (select-keys ctx [:authentication]))}}
       :authorize (fn [_ _] true)
       :yada.auth/authenticators
       [{:type :yada.http-auth-schemes.basic
         :authenticate (fn [ctx user password]
                         (future
                           (when (= [user password] ["alice" "wonderland"])
                             {:user "alice"})))}]}
      #_{:scheme "Basic"
         :realm "WallyWorld"
         :authenticate
         (fn [ctx [user password] _]
           ;; Can return ctx
           ;; nil means no creds established
           ;; non-ctx value means add to :authentication
           ;; TODO: test for each of these 3 possibilities.
           (future
             (when (= [user password] ["alice" "wonderland"])
               {:user "alice"})))}

      #_(let [response (yada/response-for resource)]
          (is (= 200 (:status response)))
          (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                 (get-in response [:headers "www-authenticate"]))))

      ]
  (yada/response-for resource)

  )
