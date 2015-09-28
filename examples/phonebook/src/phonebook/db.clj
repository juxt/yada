;; Copyright © 2015, JUXT LTD.

(ns phonebook.db)

(defn create-db [entries]
  {:phonebook (ref entries) :next-entry (ref (inc (count entries)))})

(defn add-entry
  "Add a new entry to the database"
  [db entry]
  (dosync
   (let [nextval @(:next-entry db)]
     (alter (:phonebook db) conj [nextval entry])
     (alter (:next-entry db) inc)
     nextval)))

(defn get-entries [db]
  @(:phonebook db))

(defn get-entry
  [db id]
  (get @(:phonebook db) id))

(defn count-entries [db]
  (count @(:phonebook db)))
