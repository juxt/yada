;; Copyright © 2014-2017, JUXT LTD.

(ns yada.authentication-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.core :as s]
   [schema.test :refer [deftest]]
   [yada.schema :as ys]
   [yada.security :refer [authenticate scheme-default-parameters verify]]
   [yada.test :refer [response-for]]))

;; We create some fictitious schemes, just for testing

(defmethod verify "S1"
  [ctx {:keys [authenticated]}]
  authenticated)

(defmethod verify "S2"
  [ctx {:keys [authenticated]}]
  authenticated)

(defmethod verify "S3"
  [ctx {:keys [authenticated]}]
  authenticated)

(defmethod scheme-default-parameters "S1" [ctx scheme]
  {:foo "bar"})

(defmethod scheme-default-parameters "S2" [ctx scheme]
  ;; Choose not to reveal realm
  {:realm nil
   :alt-realm "Gondor"
   :zip "AAA"})

(defn validate-ctx [ctx]
  (s/validate {:resource ys/Resource} ctx))

(deftest authenticate-test
  (testing "Across multiple realms and schemes"
    (is (=
         "S1 foo=\"bar\", realm=\"R1\", S2 alt-realm=\"Gondor\", zip=\"AAA\", S1 foo=\"bar\", realm=\"R2\", S2 alt-realm=\"Gondor\", zip=\"AAA\""
         (-> {:resource
              {:methods {}
               :access-control
               {:realms
                {"R1" {:authentication-schemes
                       [{:scheme "S1"
                         ;; TODO: Is this the right syntax? Check!
                         :verify (constantly false)}
                        {:scheme "S2"
                         :verify (constantly false)}
                        ]}
                 "R2" {:authentication-schemes
                       [{:scheme "S1"
                         :verify (constantly false)}
                        {:scheme "S2"
                         :verify (constantly false)}]}}}}}
             validate-ctx
             authenticate
             deref
             (get-in [:response :headers "www-authenticate"])))))

  (testing "Across multiple realms and schemes, with some prior authentication in one of the realms"
    (let [ctx {:resource
               {:access-control
                {:realms
                 {"R1" {:authentication-schemes
                        [{:scheme "S1"
                          :authenticated false}
                         {:scheme "S2"
                          :authenticated {:user "george" :roles #{:pig}}}
                         ]}
                  "R2" {:authentication-schemes
                        [{:scheme "S1"
                          :authenticated false}
                         {:scheme "S2"
                          :authenticated false}]}}}}}
          result (-> ctx authenticate deref)]

      ;; We have successfully verified in realm R1
      (is (= {"R1" {:user "george"
                    :roles #{:pig}}}
             (get-in result [:authentication])))

      ;; But not in realm R2, so we tell the user-agent how to do so
      (is (= "S1 foo=\"bar\", realm=\"R2\", S2 alt-realm=\"Gondor\", zip=\"AAA\""
             (get-in result [:response :headers "www-authenticate"])))))

  (testing "Authentication scheme as a function"
    (let [ctx {:resource
               {:access-control
                {:realms
                 {"default"
                  {:authentication-schemes
                   [{:authenticate (fn [ctx] {:user "george"})}]}
                  }}}}
          result @(authenticate ctx)]

      (is result)
      (is (= {:user "george"} (get-in result [:authentication "default"])))))

  (testing "No www-authenticate header produced for non-string scheme"
    (let [response
          (response-for
           {:access-control
            {:scheme :custom
             :verify (constantly nil)
             :authorization {:methods {:get "secret/view"}}}
            :methods
            {:get {:produces "text/plain"
                   :response (fn [ctx] "secret")}}}

           :get "/" {})]
      (is (= 401 (:status response)))
      (is (nil? (get-in response [:headers "www-authenticate"])))))

  (testing "authentication schemes as a function"
    (let [response
          (response-for
           {:access-control
            {:authentication-schemes (fn [ctx] [{:scheme "S3"}])
             :authorization {:methods {:get (fn [ctx] "user")}}}

            :methods
            {:get {:produces "text/plain"
                   :response (fn [ctx] "secret")}}}

           :get "/" {})]
      (is (= 401 (:status response)))
      (is (= "S3 realm=\"default\"" (get-in response [:headers "www-authenticate"]))))))

;; TODO: Authorization test

;; TODO: Investigate roles inheritance

;; (derive ::a ::b)
;; (derive ::b ::c)
;; (isa? ::a ::c)

;; Roles: Use [:and ...] for conjunctions, [:or ...] for disjunctions
