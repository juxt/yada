;; Copyright © 2015, JUXT LTD.

(ns yada.phonebook-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer (given)]

   [clojure.edn :as edn]
   [bidi.ring :refer [make-handler]]
   [ring.mock.request :refer [request]]
   [yada.test.util :refer (to-string)]
   [yada.dev.phonebook :as phonebook]))

(defn create-db [entries]
  {:phonebook (ref entries) :next-entry (ref (inc (count entries)))})

(defn create-phonebook-routes [db]
  (let [*routes (promise)
        routes (phonebook/phonebook db *routes)]
    (deliver *routes routes)
    routes))

(deftest list-all-entries
  (let [db (create-db {1 {:surname "Sparks"
                          :firstname "Malcolm"
                          :phone "1234"}

                       2 {:surname "Pither"
                          :firstname "Jon"
                          :phone "1235"}})

        h (make-handler (create-phonebook-routes db))
        req (merge-with merge
                        (request :get "/phonebook")
                        {:headers {"accept" "application/edn"}})
        response @(h req)]

    (given response
      :status := 200
      := nil)

    (given (-> response :body to-string edn/read-string)
      count := 2
      [first second :firstname] := "Malcolm")))

(deftest create-entry
  (let [db (create-db {})

        h (make-handler (create-phonebook-routes db))
        req (request :post "/phonebook" {"surname" "Pither" "firstname" "Jon" "phone" "1235"})
        response @(h req)]

    (given response
      :status := 303
      :headers :⊃ {"location" "/phonebook/1" "content-length" 0}
      :body := nil
      )))
