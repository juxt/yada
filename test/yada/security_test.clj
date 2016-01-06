;; Copyright © 2015, JUXT LTD.

(ns yada.security-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [ring.mock.request :refer [request header]]
   [yada.security :refer [authenticate authenticate-with-scheme]]))

;; We create some fictitious schemes, just for testing

(defmethod authenticate-with-scheme "S1"
  [ctx {:keys [authenticated]}]
  authenticated)

(defmethod authenticate-with-scheme "S2"
  [ctx {:keys [authenticated]}]
  authenticated)

(deftest authenticate_test
  (testing "Across multiple realms and schemes"
    (is (=
         ["S1 realm=\"R1\", S2 realm=\"R1\""
          "S1 realm=\"R2\", S2 realm=\"R2\""]
         (-> {:resource
              {:authentication
               {:realms
                {"R1" {:schemes
                       [{:scheme "S1"
                         :authenticator (constantly false)}
                        {:scheme "S2"
                         :authenticator (constantly false)}
                        ]}
                 "R2" {:schemes
                       [{:scheme "S1"
                         :authenticator (constantly false)}
                        {:scheme "S2"
                         :authenticator (constantly false)}]}}}}}

             authenticate
             (get-in [:response :headers "www-authenticate"])))))

  (testing "Across multiple realms and schemes, with some prior authentication in one of the realms"
    (let [ctx {:resource
               {:authentication
                {:realms
                 {"R1" {:schemes
                        [{:scheme "S1"
                          :authenticated false}
                         {:scheme "S2"
                          :authenticated {:user "george"
                                          :roles #{:pig}}}
                         ]}
                  "R2" {:schemes
                        [{:scheme "S1"
                          :authenticated false}
                         {:scheme "S2"
                          :authenticated false}]}}}}}
          result (authenticate ctx)]
      
      ;; We have successfully authenticated in realm R1
      (is (= {"R1" {:user "george"
                    :roles #{:pig}}
              :combined-roles #{{:realm "R1" :role :pig}}}
             (:authentication result)))

      ;; But not in realm R2, so we tell the user-agent how to do so
      (is (= ["S1 realm=\"R2\", S2 realm=\"R2\""]
             (get-in result [:response :headers "www-authenticate"]))))))



