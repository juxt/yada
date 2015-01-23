;; Copyright © 2014 JUXT LTD.

(ns yada.swagger-test
  (:require
;;   [yada.swagger :refer (map->Resource)]
   [com.stuartsierra.component :refer (system-using system-map)]
   [bidi.bidi :refer (path-for match-route)]
   [clojure.test :refer :all]
   [ring.mock.request :refer :all]
   [clojure.data.json :as json]
   [schema.core :as s]
   [bidi.ring :refer (Handle handle-request)]

   [clojure.pprint :refer (pprint)]
   [pets :refer (pets-api)]
   [pets-v1 :as pets-v1])
  (:import (yada.swagger_v1 ResourceListing)))

(deftest spec
  (testing "Spec publish"
    (let [{:keys [handler] :as mc}
          (match-route
           (pets-v1/pets-api nil) "/api/api-docs")]
      (is handler)
      (is (instance? yada.swagger_v1.ResourceListing handler))
      (is (satisfies? Handle handler))
      (is (= (set (keys mc)) #{:yada.swagger/apis
                               :yada.swagger/base-path
                               :yada.swagger/type
                               :handler}))
      (let [response (handle-request handler {} mc)]
        (is response)
        (println (:body response))

        )


      )

    ))


#_(def routes
  ["/a"
   [["/pets"
     [[""
       (map->Resource
        {:get
         {:description "Returns all pets from the system that the user has access to"
          :operationId :findPets
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
          :responses {200 {:description "pet.repsonse"
                           :schema []}}}

         :post
         {:description "Creates a new pet in the store.  Duplicates are allowed"
          :operationId :addPet}})]

      [["/" :id]
       (map->Resource
        {:get
         {:description "Returns a user based on a single ID, if the user does not have access to the pet"
          :operationId :findPetById}})]]]]])


#_(deftest match-route-test
  (let [res (match-route routes "/a/pets")]
    (is (= :findPets (get-in res [:bidi.swagger/resource :get :operationId])))))

#_(deftest path-for-test
  (is (= (path-for routes :findPets) "/a/pets"))
  (is (= (path-for routes :findPetById :id 200) "/a/pets/200")))

#_(deftest swagger-spec-test
  (let [spec (swagger-spec
              :info {:version "1.0.0"
                     :title "Swagger Petstore"}
              :paths (swagger-paths routes))]
;;    (clojure.pprint/pprint spec)
    (is (= (:swagger spec) "2.0"))
    (is (= (get-in spec [:paths "/a/pets" :get :operationId]) :findPets))
    (is (= (get-in spec [:paths "/a/pets" :post :operationId]) :addPet))
    (is (= (get-in spec [:paths "/a/pets/{id}" :get :operationId]) :findPetById))
    (println (json/write-str spec))
    (is (>= (count (json/write-str spec)) 500))))
