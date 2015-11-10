;; Copyright © 2015, JUXT LTD.

(ns phonebook.db-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]
   [phonebook.db :as db]
   [phonebook.resources-test :refer [full-seed]]))

(deftest list-all-entries
  (let [db (db/create-db full-seed)]
    (given (db/get-entries db)
      count := 2)))

(deftest create-entry
  (let [db (db/create-db {})]
    (db/add-entry db {:firstname "Jon" :surname "Pither" :phone "1235"})
    (given (db/get-entries db)
      count := 1)))

(deftest update-entry
  (let [db (db/create-db full-seed)]
    (db/update-entry db 2 {:firstname "Jon" :surname "Pither" :phone "8888"})
    (is (= (db/count-entries db) 2))
    (given (db/get-entry db 2)
      [:phone] := "8888")))

(deftest delete-entry
  (let [db (db/create-db full-seed)]
    (db/delete-entry db 1)
    (is (= (db/count-entries db) 1))))
