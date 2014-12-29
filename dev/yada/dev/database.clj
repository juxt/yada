(ns yada.dev.database
  (:require
   [com.stuartsierra.component :refer (Lifecycle)]
   [schema.core :as s]))

;; A pet store database

(defn find-pets [db]
  @(:atom db))

(defn seed-database [db]
  (reset! (:atom db)
          {1 {:name "Nemo" :animal "Fish"}})
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
