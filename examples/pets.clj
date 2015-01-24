(ns pets
  (:require
   [yada.swagger :refer (swagger)]))

(def VERSION "1.0.0")

(defn pets-api [database handler]
  (swagger
   {:info {:title "Swagger Petstore"
           :description "A sample API that uses a petstore as an example to demonstrate features in the swagger-2.0 specification"
           :terms-of-service "http://helloreverb.com/terms/"
           :contact {:name "Wordnik API Team"
                     :email "foo@example.com"
                     :url "http://madskristensen.net"}
           :license {:name "MIT"
                     :url "http://github.com/gruntjs/grunt/blob/master/LICENSE-MIT"}
           :version VERSION}

    :base-path (str "/api/" VERSION)

    :paths [["/pets"
             {:get
              {:description "Returns all pets from the system that the user has access to"
               :operation-id :find-pets
               #_:produces #_["text/html" "application/json" "text/csv"]
               :responses {200 {:description "pet response"}}

               ;; TODO: Should we rather put these in :produces?
               ;; i.e. produces is a map, replaces 'body' in yada's
               ;; handler. But turn from map into keyset when
               ;; publishing?

               ;; Do the same for consumes, for the handlers of puts and posts... ?

               :yada/handler {:body {"text/html" "These are the pets!"
                                     "application/json" (constantly [{:name "Gorilla"}])}}}

              :post
              {:description "Creates a new pet in the store. Duplicates are allowed"
               :operation-id :add-pet
               :responses {200 {:description "pet response"}}}}]

            [["/pets/" :id] {:get {:description "Returns a pet based on a single ID"}}]
            ]

    :yada/opts {:service-available? (constantly (future true))}}

   handler))
