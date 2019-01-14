;; Copyright © 2014-2017, JUXT LTD.

(ns yada.schema-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all :exclude [deftest]]
   [schema.coerce :as sc]
   [schema.core :as s]
   [schema.test :refer [deftest]]
   [schema.utils :refer [error?]]
   [yada.media-type :as mt]
   [yada.schema :refer :all]))

(def HTML (mt/string->media-type "text/html"))
(def JSON (mt/string->media-type "application/json"))
(def FORM (mt/string->media-type "application/x-www-form-urlencoded"))

(deftest produces-test
  (let [coercer (sc/coercer Produces RepresentationSeqMappings)]
    (testing "empty spec. is an error"
      (is (error? (coercer {:produces {}}))))

    (testing "as-vector"
      (is (= (coercer {:produces {:media-type #{HTML}}})
             {:produces [{:media-type HTML}]})))

    (testing "to-set"
      (is (= (coercer {:produces {:media-type HTML}})
             {:produces [{:media-type HTML}]})))

    (testing "string"
      (is (= (coercer {:produces {:media-type "text/html"}})
             {:produces [{:media-type HTML}]})))

    (testing "just-string"
      (is (= (coercer {:produces "text/html"})
             {:produces [{:media-type HTML}]})))

    (testing "string-set"
      (is (= (coercer {:produces (sorted-set "text/html" "application/json")})
             {:produces [{:media-type JSON}
                         {:media-type HTML}]})))))

(deftest consumes-test
  (let [coercer (sc/coercer Consumes RepresentationSeqMappings)]
    (testing "empty spec. is an error"
      (is (error? (coercer {:consumes {}}))))

    (testing "form"
      (is (= (coercer {:consumes "application/x-www-form-urlencoded"})
             {:consumes [{:media-type FORM}]})))))

(deftest parameters-test
  (let [coercer (sc/coercer ResourceParameters ParametersMappings)]
    (testing "none is not an error"
      (let [params (coercer {:parameters {}})]
        (is (not (error? params)))
        (is (nil? (s/check ResourceParameters params)))))
    (testing "multiple"
      (let [params (coercer {:parameters {:query {:q String}
                                          :path {:q String}}})]
        (is (not (error? params)))
        (is (nil? (s/check ResourceParameters params)))))))

(defn invoke-with-ctx [f] (f {}))

(deftest properties-test
  (let [coercer (sc/coercer Properties PropertiesMappings)]
    (testing "static"
      (let [params (coercer {:properties {}})]
        (is (not (error? params)))
        (is (nil? (s/check Properties params)))
        (is (nil? (s/check PropertiesResult (:properties params))))))

    (testing "dynamic"
      (let [params (coercer {:properties (fn [ctx] {})})]
        (is (not (error? params)))
        (is (nil? (s/check Properties params)))
        (is (nil? (s/check PropertiesResult (invoke-with-ctx (:properties params)))))))))

(deftest responses-test
  (let [coercer (sc/coercer Responses MethodsMappings)]
    (let [r (coercer {:responses {200 {:response "Hello World!"}}})]
      (is (not (error? r)))
      (is (nil? (s/check Responses r)))
      (is (= "Hello World!" (invoke-with-ctx (get-in r [:responses 200 :response]))))))
  (testing "wildcard"
    (is (= nil (s/check Responses {:responses {200 {:response (constantly "Muh")}
                                               *   {:response (constantly "Muh")}}}))))
  (testing "overlap"
    (is (nil? (s/check Responses {:responses {200                   {:response (constantly "Muh")}
                                              (set (range 201 299)) {:response (constantly "Muh")}}})))
    (is (not (nil? (s/check Responses {:responses {200        {:response (constantly "Muh")}
                                                   #{200 201} {:response (constantly "Muh")}}})))))
  (testing "disjoint sets"
    (is (nil? (s/check Responses {:responses {#{200 201} {:response (constantly "Muh")}
                                              #{202 203} {:response (constantly "Muh")}}})))
    (is (not (nil? (s/check Responses {:responses {#{200 201} {:response (constantly "Muh")}
                                                   #{201 202} {:response (constantly "Muh")}}}))))))

(deftest methods-test
  (let [coercer (sc/coercer Methods MethodsMappings)]
    (testing "methods"

      (testing "string constant"
        (let [r (coercer {:methods {:get {:response "Hello World!"}}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))
          (is (= "Hello World!" (invoke-with-ctx (get-in r [:methods :get :response]))))))

      (testing "implied handler"
        (let [r (coercer {:methods {:get (fn [ctx] "Hello World!")}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))
          (is (= "Hello World!" (invoke-with-ctx (get-in r [:methods :get :response]))))))

      (testing "nil"
        (let [r (coercer {:methods {:get nil}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))
          (is (nil? (invoke-with-ctx (get-in r [:methods :get :response]))))))

      (testing "both"
        (let [r (coercer {:methods {:get "Hello World!"}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))
          (is (= "Hello World!" (invoke-with-ctx (get-in r [:methods :get :response]))))
          (is (= "text/plain" (:name (:media-type (first (get-in r [:methods :get :produces]))))))))

      (testing "produces inside method"
        (let [r (coercer {:methods {:get {:response "Hello World!"
                                          :produces "text/plain"}}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))
          (is (= "Hello World!" (invoke-with-ctx (get-in r [:methods :get :response]))))))

      (testing "parameters"
        (let [r (coercer {:methods {:get {:parameters {:query {:q String}}
                                          :response "Hello World!"}}})]
          (is (not (error? r)))
          (is (nil? (s/check Methods r)))))

      (testing "method mappings"
        (let [r (coercer {:get "foo"})]
          (is (error? r))
          (is (= {:get 'disallowed-key} (:error r))))))))

(deftest authentication-test
  (let [coerce (sc/coercer AccessControl AccessControlMappings)]
    (testing "coerce single realm shorthand to canonical form"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]
                                   }}
               :allow-origin #{"*"}}}
             (coerce
              {:access-control {:realm "default"
                                :authentication-schemes [{:scheme "Basic"
                                                          :verify identity}]

                                :allow-origin "*"}}))))

    (testing "coerce single scheme shorthand to canonical form"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]}}}}
             (coerce
              {:access-control {:realms {"default"
                                         {:scheme "Basic"
                                          :verify identity}}}}))))

    (testing "both shorthand composed"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]}}}}
             (coerce {:access-control {:realm "default"
                                       :scheme "Basic"
                                       :verify identity}}))))))

(deftest resource-test
  (testing "produces works at both levels"
    (let [r (resource-coercer {:produces "text/html"
                               :methods {:get {:produces "text/html"
                                               :response "Hello World!"}}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r)))))

  (testing "consumes works at both levels"
    (let [r (resource-coercer {:consumes "multipart/form-data"
                               :methods {:get {:consumes "multipart/form-data"
                                               :response "Hello World!"}}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r)))))

  (testing "top-level-parameters"
    (let [r (resource-coercer {:parameters {:path {:id Long}}
                               :methods {:get "Hello World!"}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r)))))

  (testing "method-level parameters"
    (let [r (resource-coercer
             {:parameters {:path {:id Long}}
              :methods {:get {:parameters {:query {:q String}}
                              :response "Hello World!"}
                        :put {:parameters {:body String}
                              :response (fn [ctx] nil)}}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r)))))

  (testing "authorization methods"
    (let [r (resource-coercer
             {:access-control
              {:realm "accounts"
               :scheme "Custom"
               :verify identity
               :authorization {:roles/methods {:get :user}}}
              :methods {:get "SECRET!"}})]

      (is (= {:authentication-schemes
              [{:scheme "Custom"
                :verify identity}]
              :authorization {:roles/methods {:get :user}}}
             (get-in r [:access-control :realms "accounts"])))))

  (testing "swagger resource"
    (let [r (resource-coercer
             {:methods
              {:get {:summary "Get user"
                     :description "Get the details of a known user"
                     :parameters {:path {:username s/Str}}
                     :produces [{:media-type
                                 #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
                                 :charset "UTF-8"}]
                     :response (fn [ctx] "Users")
                     :responses {200 {:description "Known user"}
                                 404 {:description "Unknown user"}}}}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r)))))

  (testing "no methods, just a response"
    (let [r (resource-coercer {:response "Hello"})]
      (is (not (error? r)))
      (is (nil? (s/check Methods (select-keys r [:methods]))))
      (let [f (get-in r [:methods :get :response])]
        (is (= "Hello" (f nil))))))

  (testing "other keywords are not OK"
    (let [r (resource-coercer
             {:foo :bar
              :methods {}})]
      (is (error? r))))

  (testing "namespaced keywords are OK"
    (let [r (resource-coercer
             {:ns/foo :bar
              :methods {}})]
      (is (not (error? r)))
      (is (nil? (s/check Resource r))))))


(defn default-fn [ctx creds] ctx)

(deftest authentication-schemes-test
  (testing "Distinct authentication schemes is OK"
    (let [r (resource-coercer
             {:authentication-schemes
              [{:scheme "Basic"}
               {:scheme "Basic"}]})]
      (is r)))

  (testing "no authentication schemes is OK"
    (let [r (resource-coercer
             {:authentication-schemes []})]
      (is (not (schema.utils/error? r)))))

  (testing "cannot mix old and new authentication designs"
    (let [r (resource-coercer
             {:authentication-schemes [{:scheme "Basic"}]
              :access-control {:realms {}}})]
      (is (schema.utils/error? r))))

  (testing "authentication shorthand"
    (let [r (resource-coercer
             {:authentication {:scheme "Basic" :authenticate default-fn}})]
      (is (= {:authentication-schemes [{:scheme "Basic" :authenticate default-fn}]}
             (dissoc r :show-stack-traces?) ))))

  (testing "authentication shorthand prepends others"
    ;; This is intended to allow the usual intention of merging of
    ;; resource policies to be the most straight-forward to code.
    (let [r (resource-coercer
             {:authentication {:scheme "Basic" :authenticate default-fn}
              :authentication-schemes [{:scheme "Digest" :authenticate default-fn}]})]
      (is (= {:authentication-schemes [{:scheme "Basic" :authenticate default-fn}
                                       {:scheme "Digest" :authenticate default-fn}]}
             (dissoc r :show-stack-traces?)))))

  (testing "authentication ultra shorthand"
    ;; This is intended to allow the usual intention of merging of
    ;; resource policies to be the most straight-forward to code.
    (let [r (resource-coercer
             {:authenticate default-fn
              :authentication-schemes [{:scheme "Digest" :authenticate default-fn}]})]

      (is (= {:authentication-schemes
              [{:authenticate
                (get-in r [:authentication-schemes 0 :authenticate])}
               {:scheme "Digest" :authenticate default-fn}]}
             (dissoc r :show-stack-traces?))))))

(deftest authorization-test
  (testing "canonical authorization entry"
    (let [r (resource-coercer
             {:authorization {:authorize default-fn}})]
      (is (not (schema.utils/error? r)))
      (is (= {:authorization {:authorize default-fn}} (dissoc r :show-stack-traces?)))))

  (testing "canonical authorization entry as function"
    (let [f (fn [ctx] {:authorize default-fn})
          r (resource-coercer
             {:authorization f})]
      (is (not (schema.utils/error? r)))
      (is (= {:authorization f} (dissoc r :show-stack-traces?)))))

  (testing "shorthand"
    (let [r (resource-coercer
             {:authorize default-fn})]
      (is (not (schema.utils/error? r)))
      (is (= {:authorization {:authorize (get-in r [:authorization :authorize])}}
             (dissoc r :show-stack-traces?))))))

;; TODO: Test charsets, encodings and languages
;; TODO: Test namespaced keywords at all levels

(def user-guide-example-store-resources
  [{:summary "A list of the products we sell"
    :methods
    {:get
     {:response (io/file "index.html")
      :produces "text/html"}}}
   {:summary "Our visitor's shopping cart"
    :methods
    {:get
     {:response (fn [ctx] nil)
      :produces #{"text/html" "application/json"}}
     :post
     {:response (fn [ctx] nil)
      :produces #{"text/html" "application/json"}}}}])

(deftest user-manual-test
  (doseq [res user-guide-example-store-resources :let [r (resource-coercer res)]]
    (is (not (error? r)))))
