(ns ecto.bidi
  (:require
   [bidi.bidi :as bidi]
   [clojure.edn :as edn]
   [cheshire.core :as json]
   ecto.swagger))

#_(when (contains?
           (set (for [[k v] op] (keyword (:operationId v))))
           (:handler m))
  "")

(extend-protocol bidi/Matched
  ecto.swagger.OperationObject
  (resolve-handler [op m] (assoc m :swagger/op op))
  (unresolve-handler [op m]
    (when (= (:operationId op) (:handler m))
      "")))

(def routes
  ["/a"
   [["/pets"
     [[""
       {:get #swagger/op {:description "Returns all pets from the system that the user has access to"
                          :operationId :findPets}
        :post #swagger/op {:description "Creates a new pet in the store.  Duplicates are allowed"
                           :operationId :addPet}}]
      [["/" :id]
       {:get #swagger/op {:description "Returns a user based on a single ID, if the user does not have access to the pet"
                          :operationId :findPetById}}]
      ]]]])

(bidi/match-route routes "/a/pets" :request-method :get)

(bidi/path-for routes :findPets)
(bidi/path-for routes :findPetById :id 200)

(defn swagger-spec [routes]
  (letfn [(encode-segment [segment]
            (cond
              (keyword? segment)
              (str "{" (name segment) "}")
              :otherwise segment))

          (encode [pattern]
            (cond (vector? pattern)
                  (apply str (map encode-segment pattern))
                  :otherwise pattern))

          (paths
            ([prefix route]
             (let [[pattern matched] route]
               (let [pattern (str prefix (encode pattern))]
                 (cond (vector? matched)
                       (apply concat
                              (for [route matched]
                                (paths pattern route)))
                       :otherwise [pattern matched]))))
            ([route]
             (map vec (partition 2 (paths nil route)))))]

    {:swagger "2.0"
     :paths (into {} (paths routes))}))

;; For example, to get the swagger spec for this route structure, do this :-

(json/encode (swagger-spec routes))

;; Now finish this off, put it into bidi, including the swagger tags and swagger.
;; ecto will take the route structures, extract the swagger objects and 'handle' them
