(ns pets
  (:require
   [bidi.swagger :refer (map->Resource)]))

(def pets-resource
  (map->Resource
   {:get
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
                      :schema []}}}

    :post
    {:description "Creates a new pet in the store.  Duplicates are allowed"
     :operationId :addPet}}))

(def pet-resource
  (map->Resource
   {:get
    {:description "Returns a user based on a single ID, if the user does not have access to the pet"
     :operationId :findPetById}}))

(def pets-spec ["/" [["pets" pets-resource]
                     [["pets/" :id] pet-resource]]])
