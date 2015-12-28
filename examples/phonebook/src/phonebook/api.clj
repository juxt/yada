;; Copyright © 2015, JUXT LTD.

(ns phonebook.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using Lifecycle]]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [phonebook.www :refer [new-index-resource new-entry-resource]]
   [bidi.bidi :refer [routes-context]]
   [yada.yada :as yada :refer [yada]]))

(defn api [db *routes]
  ["/phonebook"
   [["" (yada (-> (new-index-resource db *routes)
                  (assoc :id ::index)))]
    [["/" :entry] (-> (new-entry-resource db *routes)
                      (assoc :id ::entry)
                      yada)]]])

(s/defrecord ApiComponent [db routes]
  Lifecycle
  (start [component]
    (let [p (promise)]
      ;; routes will be the overall routes structure, which we're in the
      ;; process of creating. The final value will be delivered by a
      ;; dependant.
      (assoc component
             :routes (api db p)
             :promise p)))
  (stop [component]))

(defn new-api-component []
  (->
   (map->ApiComponent {})
   (using [:db])))
