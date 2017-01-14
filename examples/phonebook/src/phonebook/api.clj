;; Copyright © 2014-2017, JUXT LTD.

(ns phonebook.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using Lifecycle]]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [phonebook.resources :refer [new-index-resource new-entry-resource]]
   [bidi.bidi :refer [routes-context]]
   [yada.yada :as yada :refer [yada]]))

(defn api [db]
  ["/phonebook"
   [["" (-> (new-index-resource db)
            (assoc :id ::index))]
    [["/" :entry] (-> (new-entry-resource db)
                      (assoc :id ::entry))]]])

(s/defrecord ApiComponent [db routes]
  Lifecycle
  (start [component]
    (assoc component
           :routes (api db)))
  (stop [component]))

(defn new-api-component []
  (->
   (map->ApiComponent {})
   (using [:db])))
