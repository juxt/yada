(ns ecto.core-test
  (:require
   [clojure.test :refer :all]
   [ecto.core :refer :all]
   [ring.mock.request :as mock]
   [clojure.core.match :refer (match)]))

(def spec
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
          :responses {200 {:description "pet.response"
                           :schema []}}}

         :post
         #bidi.swagger/op
         {:description "Creates a new pet in the store.  Duplicates are allowed"
          :operationId :addPet}})]

      [["/" :id]
       (map->Resource
        {:get
         #bidi.swagger/op
         {:description "Returns a user based on a single ID, if the user does not have access to the pet"
          :operationId :findPetById}})]]]]])

(defn get-op-response [spec req]
  (let [op (match-route spec (:uri req))
        handler (make-handler op)]
    (handler req)))

(deftest handlers
  (testing "Method Not Allowed"
    (let [response (get-op-response spec (mock/request :put "/a/pets"))]
      (is (= (-> response :status) 405))))
  (testing "OK"
    (let [response (get-op-response spec (mock/request :get "/a/pets"))]
      (is (= (-> response :status) 200)))))
