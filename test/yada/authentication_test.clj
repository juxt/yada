;; Copyright © 2014-2019, JUXT LTD.

(ns yada.authentication-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(defn encode-basic-authorization-token [user password]
  (new String (clojure.data.codec.base64/encode (.getBytes (format "%s:%s" user password)))))

#_(yada/response-for
 {:authentication
  {:scheme "Basic"
   :realm "WallyWorld"
   :authenticate
   (fn [ctx [user password]]
     ;; TODO: Test for returning ctx
     (when (= [user password] ["alice" "wonderland"])
       {:user "alice"}))}

  :methods {:get {:produces {:media-type "application/edn"}
                  :response (fn [ctx] (select-keys ctx [:authentication]))}}}

 :get "/"
 {:headers {"authorization" (format "BASIC %s" (encode-basic-authorization-token "alice" "wonderland"))}})

(deftest basic-auth-test
  (testing "basic authentication headers in response (with no authorization)"
    (let [response
          (yada/response-for
           {:authentication
            {:scheme "Basic"
             :realm "WallyWorld"
             :authenticate
             (fn [ctx [user password]]
               ;; Can return ctx
               ;; nil means no creds established
               ;; non-ctx value means add to :authentication
               ;; TODO: test for each of these 3 possibilities.
               (when (= [user password] ["alice" "wonderland"])
                 {:user "alice"}))}

            :methods {:get {:produces {:media-type "application/edn"}
                            :response (fn [ctx] (select-keys ctx [:authentication]))}}})]

      (is (= 200 (:status response)))
      (is (= ["Basic charset=\"UTF-8\", realm=\"WallyWorld\""]
             (get-in response [:headers "www-authenticate"])))))
  (testing "basic authentication headers in response (with required authorization)"
    ;; TODO: Should response with 401
    )

  (testing "")

  )
