;; Copyright © 2015, JUXT LTD.

(ns phonebook.resources-test
  (:require
   [byte-streams :as b]
   [bidi.bidi :as bidi]
   [bidi.ring :refer [make-handler]]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.test :refer :all]
   [clojure.edn :as edn]
   [ring.mock.request :refer [request]]
   [phonebook.util :refer [to-string]]
   [phonebook.db :as db]
   [phonebook.api :refer [api]]))

(def full-seed {1 {:surname "Sparks"
                   :firstname "Malcolm"
                   :phone "1234"}

                2 {:surname "Pither"
                   :firstname "Jon"
                   :phone "1235"}})

(defn create-api [db]
  (let [*routes (promise)
        api (api db *routes)]
    (deliver *routes api)
    api))

(deftest list-all-entries
  (let [db (db/create-db full-seed)
        h (make-handler (create-api db))
        req (merge-with merge
                        (request :get "/phonebook")
                        {:headers {"accept" "application/edn"}})
        response @(h req)]

    (is (= 200 (:status response)))

    (let [body (-> response :body to-string edn/read-string)]
      (is (= 2 (count body)))
      (is (= "Malcolm" (get-in body [1 :firstname]))))))

(deftest create-entry
  (let [db (db/create-db {})
        h (make-handler (create-api db))
        req (request :post "/phonebook" {"surname" "Pither" "firstname" "Jon" "phone" "1235"})
        response @(h req)]

    (is (= 303 (:status response)))
    (is (set/superset? (set (keys (:headers response)))
                       #{"location" "content-length"}))
    (is (nil? (:body response)))))

(deftest update-entry
  (let [db (db/create-db full-seed)
        h (make-handler (create-api db))]
    (is (= (db/count-entries db) 2))
    (let [req (->
               (request :put "/phonebook/2" (slurp (io/resource "phonebook/update-data")))
               (assoc-in [:headers "content-type"] "multipart/form-data; boundary=ABCD"))
          response @(h req)]

      (is (= 204 (:status response)))
      (is (nil? (:body response)))

      (is (= (db/count-entries db) 2))
      (is (= "8888" (:phone (db/get-entry db 2)))))))

(deftest delete-entry
  (let [db (db/create-db full-seed)
        h (make-handler (create-api db))]
    (is (= (db/count-entries db) 2))
    (let [req (request :delete "/phonebook/1")
          response @(h req)]
      (is (= 204 (:status response)))
      (is (nil? (:body response)))
      (is (= (db/count-entries db) 1)))))
