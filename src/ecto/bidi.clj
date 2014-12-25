(ns ecto.bidi
  (:require
   bidi.swagger
   [bidi.bidi :as bidi]
   [bidi.swagger :as swagger]
   [cheshire.core :as json]))

(def routes
  ["/a"
   [["/pets"
     [[""
       {:get #bidi.swagger/op {:description "Returns all pets from the system that the user has access to"
                               :operationId :findPets}
        :post #bidi.swagger/op {:description "Creates a new pet in the store.  Duplicates are allowed"
                                :operationId :addPet}}]
      [["/" :id]
       {:get #bidi.swagger/op {:description "Returns a user based on a single ID, if the user does not have access to the pet"
                               :operationId :findPetById}}]
      ]]]])

(bidi/match-route routes "/a/pets" :request-method :get)
(bidi/path-for routes :findPets)
(bidi/path-for routes :findPetById :id 200)

(swagger/json-swagger-spec routes)

;; example, to get the swagger spec for this route structure, do this :-

;; Now finish this off, put it into bidi, including the swagger tags and swagger.
;; ecto will take the route structures, extract the swagger objects and 'handle' them
