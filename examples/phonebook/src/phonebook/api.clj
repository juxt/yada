;; Copyright © 2015, JUXT LTD.

(ns phonebook.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer [html]]
   [modular.component.co-dependency :refer [co-using]]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [phonebook.www :refer [new-index-resource new-entry-resource]]
   [yada.yada :refer [yada]]))

(defn api [db *routes]
  ["/phonebook"
   {"" (yada (merge (new-index-resource db *routes)
                    {:id ::index}))
    ["/" :entry] (yada (merge (new-entry-resource db *routes)
                              {:id ::entry}))}])

(s/defrecord ApiComponent [db *server]
  RouteProvider
  (routes [_]
          (let [*routes (delay (:routes @*server))]
            (api db *routes))))

(defn new-api-component []
  (->
   (map->ApiComponent {})
   (using [:db])
   (co-using [:server])))
