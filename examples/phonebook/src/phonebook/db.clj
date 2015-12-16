;; Copyright © 2015, JUXT LTD.

(ns phonebook.db
  (:require
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [phonebook.schema :refer [Phonebook PhonebookEntry]]))

(s/defn create-db [entries :- Phonebook]
  (assert entries)
  {:phonebook (ref entries)
   :next-entry (ref (if (not-empty entries)
                      (inc (apply max (keys entries)))
                      1))})

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

(defn update-entry
  "Update a new entry to the database. Returns the id of the newly added
  entry."
  [db id entry]
  (dosync
   (alter (:phonebook db) assoc id entry)))

(defn delete-entry
  "Delete a entry from the database."
  [db id]
  (dosync
   (alter (:phonebook db) dissoc id)))

(s/defn get-entries :- Phonebook
  [db]
  @(:phonebook db))

(s/defn matches? [q :- String
                 entry :- PhonebookEntry]
  (some (partial re-seq (re-pattern (str "(?i:\\Q" q "\\E)")))
        (map str (vals (second entry)))))

(s/defn search-entries :- Phonebook
  [db q]
  (let [entries (get-entries db)
        f (filter (partial matches? q) entries)]
    (into {} f)))

(s/defn get-entry :- (s/maybe PhonebookEntry)
  [db id]
  (get @(:phonebook db) id))

(s/defn count-entries :- s/Int
  [db]
  (count @(:phonebook db)))
