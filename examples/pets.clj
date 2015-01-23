(ns pets
  (:require
   [yada.swagger :refer (swagger op)]))

(def VERSION "1.0.0")

(defn pets-api [database]
  (swagger
   {:info {:version VERSION
           :title "Swagger Petstore"
           :description "A sample API that uses a petstore as an example to demonstrate features in the swagger-2.0 specification"
           :terms-of-service "http://helloreverb.com/terms/"
           :contact {:name "Wordnik API Team"
                     :email "foo@example.com"
                     :url "http://madskristensen.net"}
           :license {:name "MIT"
                     :url "http://github.com/gruntjs/grunt/blob/master/LICENSE-MIT"}}
    :base-path "/api/1.0.0"

    :paths {"/pets" {:get (op {:description "Returns all pets from the system that the user has access to"
                               :operation-id :find-pets
                               :responses {200 {:description "pet response"}}})
                     :post (op {:description "Creates a new pet in the store. Duplicates are allowed"
                                :operation-id :add-pet
                                :responses {200 {:description "pet response"}}})}
            ["/pets/" :id] {:get {:description "Returns a pet based on a single ID"}}}}))
