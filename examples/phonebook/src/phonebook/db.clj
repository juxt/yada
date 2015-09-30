;; Copyright © 2015, JUXT LTD.

(ns phonebook.db)

(defn create-db [entries]
  {:phonebook (ref entries)
   :next-entry (ref (inc (apply max (keys entries))))})

(defn add-entry
  "Add a new entry to the database. Returns the id of the newly added
  entry."
  [db entry]
  (dosync
   ;; Why use 2 refs when one atom would do? It comes down to being able
   ;; to return nextval from this function. While this is possible to do
   ;; with an atom, its feels less elegant.
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
