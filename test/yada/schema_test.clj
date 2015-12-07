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
      (given (coercer {:parameters {}})
             identity :? (comp not error?)
             identity :- ResourceParameters))
    (testing "multiple"
      (given (coercer {:parameters {:query {:q String}
                                    :path {:q String}}})
             identity :? (comp not error?)
             identity :- ResourceParameters))))

(defn invoke-with-ctx [f] (f {}))

(deftest properties-test
  (let [coercer (sc/coercer Properties PropertiesMappings)]
    (testing "static"
      (given (coercer {:properties {}})
             identity :? (comp not error?)
             identity :- Properties
             :properties :- PropertiesResult
             ))
    (testing "dynamic"
      (given (coercer {:properties (fn [ctx] {})})
             identity :? (comp not error?)
             identity :- Properties
             [:properties invoke-with-ctx] :- PropertiesResult))))

(deftest methods-test
  (let [coercer (sc/coercer Methods MethodsMappings)]
    (testing "methods"

      (testing "string constant"
        (given (coercer {:methods {:get {:handler "Hello World!"}}})
               identity :? (comp not error?)
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"))

      
      (testing "implied handler"
        (given (coercer {:methods {:get (fn [ctx] "Hello World!")}})
               identity :? (comp not error?)
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"))

      (testing "nil"
        (given (coercer {:methods {:get nil}})
               identity :? (comp not error?)
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := nil))

      (testing "both"
        (given (coercer {:methods {:get "Hello World!"}})
               identity :? (comp not error?)
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"
               [:methods :get :produces first :media-type :name] := "text/plain"))

      (testing "produces inside method"
        (given (coercer {:methods {:get {:handler "Hello World!"
                                         :produces "text/plain"}}})
               identity :? (comp not error?)
               identity :- Methods
               [:methods :get :handler invoke-with-ctx] := "Hello World!"
               ;;[:methods :get] := "foo"
               ))

      (testing "parameters"
        (given (coercer {:methods {:get {:parameters {:query {:q String}}
                                         :handler "Hello World!"}}})
               identity :? (comp not error?)
               identity :- Methods)))))

(deftest resource-test
  (testing "produces works at both levels"
    (given (resource-coercer {:produces "text/html"
                              :methods {:get {:produces "text/html"
                                              :handler "Hello World!"}}})
           identity :? (comp not error?)
           identity :- Resource))

  (testing "consumes works at both levels"
    (given (resource-coercer {:consumes "multipart/form-data"
                              :methods {:get {:consumes "multipart/form-data"
                                              :handler "Hello World!"}}})
           identity :? (comp not error?)
           identity :- Resource))

  (testing "top-level-parameters"
    (given (resource-coercer {:parameters {:path {:id Long}}
                              :methods {:get "Hello World!"}})
           identity :? (comp not error?)
           identity :- Resource))

  (testing "method-level parameters"
    (given (resource-coercer
            {:parameters {:path {:id Long}}
             :methods {:get {:parameters {:query {:q String}}
                             :handler "Hello World!"}
                       :put {:parameters {:body String}
                             :handler (fn [ctx] nil)}}})
           identity :? (comp not error?)
           identity :- Resource))

  (testing "swagger resource"
    (given (resource-coercer
            {:methods
             {:get {:summary "Get user"
                    :description "Get the details of a known user"
                    :parameters {:path {:username s/Str}}
                    :produces [{:media-type
                                #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
                                :charset "UTF-8"}]
                    :handler (fn [ctx] "Users")
                    :responses {200 {:description "Known user"}
                                404 {:description "Unknown user"}}}}})
           identity :? (comp not error?)
           identity :- Resource))

  (testing "other keywords are not OK"
    (given (resource-coercer
            {:foo :bar
             :methods {}})
           identity :? error?))

  (testing "namespaced keywords are OK"
    (given (resource-coercer
            {:ns/foo :bar
             :methods {}})
           identity :? (comp not error?)
           identity :- Resource)))


