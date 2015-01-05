;; Copyright © 2015, JUXT LTD.

(ns yada.dev.database
  (:require
   [com.stuartsierra.component :refer (Lifecycle)]
   [schema.core :as s]))

;; A pet store database

(defn seed-database [db]
  (reset! (:atom db)
          {"001" {:name "Nemo" :animal "Fish"}
           "002" {:name "George" :animal "Monkey"}
           "003" {:name "Special Patrol Group" :animal "Hamster"}})
  db)

(defrecord Database []
  Lifecycle
  (start [component]
    (seed-database (assoc component :atom (atom {}))))
  (stop [component] component))

(def new-database-schema {})

(defn new-database [& {:as opts}]
  (->> opts
    (merge {})
    (s/validate new-database-schema)
    map->Database))
