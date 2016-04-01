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
            [ring.swagger.swagger2-schema :as rsws]))

;; TODO: Build a proper swagger_test suite

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
    (is (= {:paths {"/api" {:get {:parameters {:path {:id String}
                                               :query {:id Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods {:get {:parameters {:query {:id Long}}
                                                :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:parameters {:path {:id String
                                                      :age Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods {:get {:parameters {:path {:age Long}}
                                                :response (constantly nil)}}})])))
    (is (= {:paths {"/api" {:get {:parameters {:path {:id Long}}}}}}
           (routes->ring-swagger-spec
             ["/api" (resource {:parameters {:path {:id String}}
                                :methods {:get {:parameters {:path {:id Long}}
                                                :response (constantly nil)}}})])))
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
                                                     :response   (constantly nil)}}})]))))))

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
