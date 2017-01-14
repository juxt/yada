;; Copyright © 2014-2017, JUXT LTD.

(ns phonebook.db-test
  (:require
   [clojure.test :refer :all]
   [phonebook.db :as db]
   [phonebook.resources-test :refer [full-seed]]))

(deftest list-all-entries
  (let [db (db/create-db full-seed)]
    (is (= 2 (count (db/get-entries db))))))

(deftest create-entry
  (let [db (db/create-db {})]
    (db/add-entry db {:firstname "Jon" :surname "Pither" :phone "1235"})
    (is (= 1 (count (db/get-entries db))))))

(deftest update-entry
  (let [db (db/create-db full-seed)]
    (db/update-entry db 2 {:firstname "Jon" :surname "Pither" :phone "8888"})
    (is (= (db/count-entries db) 2))
    (is "8888" (get-in (db/get-entry db 2) [:phone]))))

(deftest delete-entry
  (let [db (db/create-db full-seed)]
    (db/delete-entry db 1)
    (is (= (db/count-entries db) 1))))
