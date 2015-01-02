;; Copyright © 2015, JUXT LTD.

(ns pets
  (:require
   [bidi.swagger :refer (map->SwaggerOperation)]))

(def find-pets
  (map->SwaggerOperation
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
                     :schema []}}}))

(def add-pet
  (map->SwaggerOperation
   {:description "Creates a new pet in the store.  Duplicates are allowed"
    :operationId :addPet}))

(def find-pet-by-id
  (map->SwaggerOperation
   {:description "Returns a user based on a single ID, if the user does not have access to the pet"
    :operationId :findPetById
    :produces ["application/json" "application/xml" "text/xml" "text/html"]
}))

(def pets-spec
  ["/"
   [["pets" {:get find-pets
             :post add-pet}]
    [["pets/" :id] {:get find-pet-by-id}]]])
