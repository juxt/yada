;; Copyright © 2015, JUXT LTD.

(ns yada.swagger-test
  (:require [yada.swagger :refer :all]
            [clojure.test :refer :all]
            [yada.yada :refer [as-resource]]
            [yada.schema :as ys]
            [clojure.set :as set]
            [schema.core :as s]
            [schema.coerce :as sc]
            [ring.swagger.swagger2 :as rsw]
            [ring.swagger.swagger2-schema :as rsws]))

;; TODO: Build a proper swagger_test suite


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
