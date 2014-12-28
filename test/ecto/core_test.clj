(ns ecto.core-test
  (:require
   [clojure.test :refer :all]
   [ecto.core :refer :all]
   [ring.mock.request :as mock]
   [clojure.core.match :refer (match)]
   [manifold.deferred :as d]))

(def spec
  ["/a"
   [["/pets"
     [[""
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
         #bidi.swagger/op
         {:description "Creates a new pet in the store.  Duplicates are allowed"
          :operationId :addPet}})]

      [["/" :id]
       (map->Resource
        {:get
         #bidi.swagger/op
         {:description "Returns a user based on a single ID, if the user does not have access to the pet"
          :operationId :findPetById}})]]]]])

(defn get-op-response [spec req & {:as opts}]
  (let [op (match-route spec (:uri req))
        handler (make-handler op opts)]
    (let [res (handler req)]
      (if (d/deferrable? res) @res res))))

(deftest handlers
  (testing "Service Unavailable"
    (let [response (get-op-response spec (mock/request :get "/a/pets")
                                    :service-available? false)]
      (is (= (-> response :status) 503)))
    (let [response (get-op-response spec (mock/request :get "/a/pets")
                                    :service-available? true)]
      (is (not= (-> response :status) 503))))

  (testing "Not Implemented"
    (let [response (get-op-response spec (mock/request :play "/a/pets"))]
      (is (= (-> response :status) 501)))
    (let [response (get-op-response spec (mock/request :play "/a/pets")
                                    :known-method? (fn [x] (= x :play)))]
      (is (not= (-> response :status) 501))))

  (testing "Request URI Too Long"
    (let [response (get-op-response spec (mock/request :get "/a/pets")
                                    :request-uri-too-long? 4)]
      (is (= (-> response :status) 414))))

  (testing "Method Not Allowed"
    (let [response (get-op-response spec (mock/request :put "/a/pets"))]
      (is (= (-> response :status) 405))))

  (testing "OK"
    (let [response (get-op-response spec (mock/request :get "/a/pets"))]
      (is (= (-> response :status) 200))
      (is (= (-> response :body) "Hello World!"))))

  (testing "Not found"
    (let [response (get-op-response spec (mock/request :get "/a/pets") :resource-metadata (constantly nil))]
      (is (= (-> response :status) 404)))))

;; TODO: Auth
;; TODO: Conneg
;; TODO: CORS/OPTIONS
;; TODO: CSRF
;; TODO: Cache-headers
