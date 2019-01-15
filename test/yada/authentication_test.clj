;; Copyright © 2014-2019, JUXT LTD.

(ns yada.authentication-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]
   [clojure.tools.logging :as log]))

(defn encode-basic-authorization-token [user password]
  (new String (clojure.data.codec.base64/encode (.getBytes (format "%s:%s" user password)))))

#_(yada/response-for
 {:methods {:get {:produces {:media-type "application/edn"}
                  :response (fn [ctx] (select-keys ctx [:credentials]))}}
  :authenticate (fn [ctx claims]
                  (log/infof "authenticate")
                  {:user "fred"}
                  )})

#_(deftest authenticate-test
  (let [resource
        {:methods {:get {:produces {:media-type "application/edn"}
                         :response (fn [ctx] (select-keys ctx [:credentials]))}}
         :authenticate (fn [ctx claims]
                         (log/infof "authenticate")
                         {:user "fred"}
                         )}
        ])
   )

(deftest basic-auth-test
  (let [resource
        {:methods {:get {:produces {:media-type "application/edn"}
                         :response (fn [ctx] (select-keys ctx [:credentials]))}}}]

    (let [resource
          (assoc
           resource
           :authentication
           {:scheme "Basic"
            :realm "WallyWorld"
            :authenticate
            (fn [ctx [user password] _]
              ;; Can return ctx
              ;; nil means no creds established
              ;; non-ctx value means add to :authentication
              ;; TODO: test for each of these 3 possibilities.
              (future
                (when (= [user password] ["alice" "wonderland"])
                  {:user "alice"})))})]

      (testing "basic authentication challenge in response when no credentials"
        (let [response (yada/response-for resource)]
          (is (= 200 (:status response)))
          (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                 (get-in response [:headers "www-authenticate"])))))

      (testing "no basic authentication challenge when satisfactory credentials"
        (let [response (yada/response-for
                        resource :get "/"
                        {:headers {"authorization"
                                   (format "BASIC %s" (encode-basic-authorization-token "alice" "wonderland"))}})]
          (is (= 200 (:status response)))
          (is (nil? (get-in response [:headers "www-authenticate"])))))

      (testing "basic authentication challenge when wrong credentials"
        (let [response (yada/response-for
                        resource :get "/"
                        {:headers {"authorization"
                                   (format "BASIC %s" (encode-basic-authorization-token "alice" "pa$$w0rd"))}})]
          (is (= 200 (:status response)))
          (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                 (get-in response [:headers "www-authenticate"])))))

      (testing "401 challenge due to no authorization header on a protected resource"
        (let [resource (assoc
                        resource
                        :authorize (fn [ctx creds] nil)
                        )]
          (let [response (yada/response-for resource)]
            (is (= 401 (:status response)))
            (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
                   (get-in response [:headers "www-authenticate"]))))))

      (testing "401 challenge due to bad credentials"
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

      (testing "forbidden when good credentials"
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

(comment
  (let [resource
        {:methods {:get {:produces {:media-type "application/edn"}
                         :response (fn [ctx] (select-keys ctx [:authentication]))}}
         :authentication
         {:scheme "Basic"
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
         :authorize (fn [_ _] nil)}]

    (yada/response-for resource)))
