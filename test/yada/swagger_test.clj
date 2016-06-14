;; Copyright © 2015, JUXT LTD.

(ns yada.swagger-test
  (:require [yada.swagger :refer :all]
            [clojure.test :refer :all]
            [yada.yada :refer [as-resource yada resource]]
            [yada.schema :as ys]
            [clojure.set :as set]
            [schema.core :as s]
            [schema.coerce :as sc]
            [ring.swagger.swagger2 :as rsw]
            [ring.swagger.swagger2-schema :as rsws]
            [bidi.bidi :as bidi]
            [clojure.walk :refer [postwalk]])
  (:import (java.util.regex Pattern)
           (clojure.lang Keyword)))

;; TODO: Build a proper swagger_test suite

(defn replace-pattern
  "regex Pattern instances with the same pattern are not equal so this helper replaces any regex
  in form that has the same pattern as the argument with the argument so that we can compare with ="
  [pattern form]
  (postwalk
    (fn [item]
      (if (and (instance? Pattern item)
               (= (.pattern item) (.pattern pattern)))
        pattern
        item))
    form))

(deftest test-routes->ring-swagger-spec
  (testing "simple"
    (is (= {:info  {:version "2.0"
                    :title   "My API"}
            :paths {"" {:get {:produces ["text/plain"]}}}}
           (routes->ring-swagger-spec
             ["" (yada "test")]
             {:info {:version "2.0"
                     :title   "My API"}})))
    (is (= {:paths {"/api" {:get {:parameters {:formData {:q String}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:methods {:get {:parameters {:form {:q String}}
                                                :response   (fn [_] nil)}}})])))
    (is (= {:paths {"/api" {:get  {:description "Get the right data"
                                   :summary     "Get"
                                   :produces    ["text/html"]
                                   :consumes    ["text/plain"]
                                   :parameters  {:query {:q String}}}
                            :post {:summary    "POST item"
                                   :parameters {:formData {:name String}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:methods {:get  {:description "Get the right data"
                                                 :summary     "Get"
                                                 :produces    "text/html;pretty=true"
                                                 :consumes    "text/plain"
                                                 :parameters  {:query {:q String}}
                                                 :response    (fn [_] nil)}
                                          :post {:summary    "POST item"
                                                 :parameters {:form {:name String}}
                                                 :response   (constantly nil)}}})]))))


  (testing "resource & method merge"
    (is (= {:paths {"/api" {:get {:produces ["text/html" "text/xml" "text/plain"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:produces ["text/html" "text/xml"]
                                :methods  {:get {:produces ["text/html" "text/html;pretty=true" "text/plain"]
                                                 :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:consumes ["text/html" "text/xml" "text/plain"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:consumes ["text/html" "text/xml"]
                                :methods  {:get {:consumes ["text/html" "text/html;pretty=true" "text/plain"]
                                                 :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:produces ["text/html" "text/xml"]
                                  :consumes ["text/html" "text/plain"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:produces ["text/html" "text/xml"]
                                :methods  {:get {:consumes ["text/html" "text/html;pretty=true" "text/plain"]
                                                 :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:consumes ["text/html" "text/xml"]
                                  :produces ["text/html" "text/plain"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:consumes ["text/html" "text/xml"]
                                :methods  {:get {:produces ["text/html" "text/html;pretty=true" "text/plain"]
                                                 :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:parameters {:path  {:id String}
                                               :query {:id Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods    {:get {:parameters {:query {:id Long}}
                                                   :response   (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:parameters {:path {:id  String
                                                      :age Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods    {:get {:parameters {:path {:age Long}}
                                                   :response   (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:parameters {:path {:id Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods    {:get {:parameters {:path {:id Long}}
                                                   :response   (constantly nil)}}})])))
    (let [pattern-1 #"[a-zA-Z]*"
          pattern-2 #"[a-zA-Z0-9]+"]
      (is (= {:paths {"/api" {:get {:parameters {:path     {:id      String
                                                            :user_id Long
                                                            :format  String}
                                                 :query    {:color    s/Keyword
                                                            :filter   Long
                                                            :includes pattern-1}
                                                 :header   {:token pattern-2
                                                            :dairy #{:milk :cheese}
                                                            :ext   [String]}
                                                 :body     Long
                                                 :formData {:t_id Long}}}}}}
             (routes->ring-swagger-spec
               ["/api" (resource {:parameters {:path   {:id      String
                                                        :user_id String}
                                               :query  {:color  s/Keyword
                                                        :filter String}
                                               :cookie {:session Long
                                                        :time    Long}
                                               :header {:token pattern-2
                                                        :dairy #{:milk :cream}}}
                                  :methods    {:get {:parameters {:body   Long
                                                                  :form   {:t_id Long}
                                                                  :path   {:user_id Long
                                                                           :format  String}
                                                                  :query  {:filter   Long
                                                                           :includes pattern-1}
                                                                  :cookie {:session   String
                                                                           :privilege String}
                                                                  :header {:dairy #{:milk :cheese}
                                                                           :ext   [String]}}
                                                     :response   (constantly nil)}}})])))))
  (testing "route path parameters"
    (is (= {:paths {"/api/{id}" {:get {:produces   ["text/plain"]
                                       :parameters {:path {:id String}}}}}}
           (routes->ring-swagger-spec [["/api/" :id] (yada "test")])))
    (let [pattern #"[A-Za-z]+[A-Za-z0-9\*\+\!\-\_\?\.]*(?:%2F[A-Za-z]+[A-Za-z0-9\*\+\!\-\_\?\.]*)?"]
      (is (= {:paths {"/api/{id}" {:get {:produces   ["text/plain"]
                                         :parameters {:path {:id pattern}}}}}}
             (replace-pattern pattern
                              (routes->ring-swagger-spec [["/api/" [keyword :id]] (yada "test")])))))
    (is (= {:paths {"/api/{id}" {:get {:produces   ["text/plain"]
                                       :parameters {:path {:id Long}}}}}}
           (routes->ring-swagger-spec [["/api/" [long :id]] (yada "test")])))
    (let [pattern #"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"]
      (is (= {:paths {"/api/{id}" {:get {:produces   ["text/plain"]
                                         :parameters {:path {:id pattern}}}}}}
             (replace-pattern pattern
                              (routes->ring-swagger-spec [["/api/" [bidi/uuid :id]] (yada "test")])))))
    (let [pattern #".+\@.+\..+"]
      (is (= {:paths {"/api/{email}" {:get {:produces   ["text/plain"]
                                            :parameters {:path {:email pattern}}}}}}
             (routes->ring-swagger-spec [["/api/" [pattern :email]] (yada "test")]))))
    (testing "resource or method path parameters replace"
      (is (= {:paths {"/api/{id}" {:get {:parameters {:path  {:id String}
                                                      :query {:name Long}}}}}}
             (routes->ring-swagger-spec
               [["/api/" :id] (resource {:methods {:get {:parameters {:query {:name Long}}
                                                         :response   (constantly nil)}}})])))
      (is (= {:paths {"/api/{id}" {:get {:parameters {:path  {:id String}
                                                      :query {:name Long}}}}}}
             (routes->ring-swagger-spec
               [["/api/" :id] (resource {:parameters {:query {:name Long}}
                                         :methods    {:get {:response (constantly nil)}}})])))
      (is (= {:paths {"/api/{id}" {:get {:parameters {:path {:time String}}}}}}
             (routes->ring-swagger-spec
               [["/api/" [long :id]] (resource {:methods {:get {:parameters {:path {:time String}}
                                                                :response   (constantly nil)}}})])))
      (is (= {:paths {"/api/{id}" {:get {:parameters {:path {:name String}}}}}}
             (routes->ring-swagger-spec
               [["/api/" [long :id]] (resource {:parameters {:path {:name String}}
                                                :methods    {:get {:response (constantly nil)}}})])))
      (is (= {:paths {"/api/{id}" {:get {:parameters {:path {:name String
                                                             :time String}}}}}}
             (routes->ring-swagger-spec
               [["/api/" [long :id]] (resource {:parameters {:path {:name String}}
                                                :methods    {:get {:parameters {:path {:time String}}
                                                                   :response   (constantly nil)}}})])))))
  (testing "responses"
    (is (= {:paths {"/api" {:get {:responses {200      {:description "OK"}
                                              301      {:description "Redirect"}
                                              302      {:description "Redirect"}
                                              :default {:description "default"}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:methods {:get {:responses {200        {:description "OK"}
                                                            #{301 302} {:description "Redirect"}
                                                            *          {:description "default"}}
                                                :response  (fn [_] nil)}}})])))
    (is (= {:paths {"/api" {:get {:responses {200      {:description "OK"}
                                              301      {:description "Redirect"}
                                              302      {:description "Redirect"}
                                              400      {:description "Bad"}
                                              :default {:description "default"}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:responses {#{302 400} {:description "Bad"
                                                        :produces    "text/plain"
                                                        :response (constantly nil)}}
                                :methods {:get {:responses {200        {:description "OK"}
                                                            #{301 302} {:description "Redirect"}
                                                            *          {:description "default"}}
                                                :response  (fn [_] nil)}}})]))))
  (testing "swagger namespace keys"
    (is (= {:paths {"/api" {:get {:tags ["test"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:methods {:get {:swagger/tags ["test"]
                                                :response   (fn [_] nil)}}})])))
    (is (= {:paths {"/api" {:get {:tags ["test"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:swagger/tags ["test"]
                                :methods {:get {:response   (fn [_] nil)}}})])))
    (is (= {:paths {"/api" {:get {:tags ["get-stuff"]}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:swagger/tags ["test"]
                                :methods {:get {:swagger/tags ["get-stuff"]
                                                :response   (fn [_] nil)}}})])))
    (is (= {:paths {"/api" {:get {:responses {200 {:description "OK"
                                                   :schema String}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:methods      {:get {:responses {200 {:description "OK"
                                                                      :swagger/schema String}}
                                                     :response  (fn [_] nil)}}})])))
    ))

#_(select-keys
 (get-in (as-resource "Hello World!") [:methods :get])
 [:parameters]
 )


#_(resource {:methods {:get {:parameters {:query {:q String}}
                             :response (fn [ctx] nil)}}})

#_(rsw/swagger-json
   (s/validate rsws/Swagger
               {:info {:version "2.0"
                       :title "My API"}
                :produces []
                :consumes []
                :paths {"/abc"
                        {:get
                         ((sc/coercer
                           rsws/Operation
                           {rsws/Parameters #(set/rename-keys % {:form :formData})})

                          {:parameters {:form {:foo String}}
                           :response (fn [ctx] nil)}
                          )}}}))
