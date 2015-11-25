;; Copyright © 2015, JUXT LTD.

(ns yada.schema-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [juxt.iota :refer [given]]
   [yada.media-type :as mt]
   [yada.schema :refer :all]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.test :refer [deftest]]
   [schema.utils :refer [error?]]))

(def HTML (mt/string->media-type "text/html"))
(def JSON (mt/string->media-type "application/json"))

(deftest produces-test
  (let [coercer (sc/coercer Produces RepresentationSeqMappings)]
    (testing "produces"
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
                           {:media-type HTML}]}))))))

(defn invoke-with-ctx [f] (f {}))

(deftest methods-test
  (let [coercer (sc/coercer Methods MethodsMappings)]
    (testing "methods"
      (testing "string constant"
        (given (coercer {:methods {:get {:handler "Hello World!"}}})
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"))

      (testing "implied handler"
        (given (coercer {:methods {:get (fn [ctx] "Hello World!")}})
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"))

      (testing "both"
        (given (coercer {:methods {:get "Hello World!"}})
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"
               [:methods :get :produces first :media-type :name] := "text/plain"))

      (testing "produces inside method"
        (given (coercer {:methods {:get {:handler "Hello World!"
                                         :produces "text/plain"}}})
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"
               ;;[:methods :get] := "foo"
               )))))

(deftest combo-test
  (testing "produces works at both levels"
    (given (resource-coercer {:produces "text/html"
                              :methods {:get {:produces "text/html"
                                              :handler "Hello World!"}}})
           identity :- Resource)))


