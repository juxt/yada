;; Copyright © 2015, JUXT LTD.

(ns pets
  (:require
   [yada.swagger :refer (->ResourceListing
                         ->Resource
                         map->Operation)
    :rename {->ResourceListing rl
             ->Resource r
             map->Operation op}]
   [manifold.deferred :as d]
   [bidi.bidi :as bidi]))

(defn find-pets [db]
  @(:atom db))

(defn find-pet-by-id [db id]
  (when-let [row (get @(:atom db) id)]
    (assoc row :id id)))

(def VERSION "1.0.0")

(defn pets-api [database]
  [(format "/api/%s/" VERSION)
   (rl
    {:api-version VERSION
     :info {:title "Swagger Sample App"
            :description "This is a sample server Petstore server"
            :termsOfServiceUrl "http://helloreverb.com/terms/"
            :contact "apiteam@wordnik.com",
            :license "Apache 2.0",
            :licenseUrl "http://www.apache.org/licenses/LICENSE-2.0.html"
            }}
    [["pet" (r
             {:description "Operations about pets"
              :produces ["application/json" "application/xml" "text/plain" "text/html"]}
             [["pets"
               {:get
                (op
                 {:description "Returns all pets from the system that the user has access to"
                  :operationId :findPets
                  :scopes [""]
                  :produces ["application/json" "application/xml" "text/xml" "text/html"]
                  :parameters [{:name "tags" :in :query :description "tags to filter by"
                                :required false
                                :type :array
                                :items {:type :string}
                                :collectionFormat :csv}
                               {:name "limit"
                                :in :query
                                :description "maximum number of results to return"
                                :required false
                                :type :integer
                                :format :int32}]
                  :responses {200 {:description "pet.response"
                                   :schema []}}

                  :handler
                  {:entity (fn [resource] (d/future (find-pets database)))
                   :body {}}

                  })
                :post
                (op
                 {:description "Creates a new pet in the store.  Duplicates are allowed"
                  :operationId :addPet
                  :body "TODO: addPet body"})}]

              [["pets/" :id]
               {:get
                (op
                 {:description "Returns a user based on a single ID, if the user does not have access to the pet"
                  :operationId :findPetById
                  :produces ["application/json" "application/xml" "text/xml" "text/html"]

                  :handler {:find-resource (fn [opts] {:id (-> opts :params :id)})
                            :entity (fn [{id :id}] (d/future (find-pet-by-id database id)))}
                  })}]])]
     ["user" (r {:description "Operations about user"} [])]
     ["store" (r {:description "Operations about store"} [])]
     ])])
