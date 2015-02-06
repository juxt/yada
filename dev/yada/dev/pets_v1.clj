;; Copyright © 2015, JUXT LTD.

(ns yada.dev.pets-v1
  (:require
   [com.stuartsierra.component :refer (Lifecycle using)]
   [bidi.bidi :refer (RouteProvider)]
   [schema.core :as s]
   [pets-v1 :as pets]
   ))

(defrecord ApiService [database]
  Lifecycle
  (start [this] (assoc this :api (pets/pets-api database)))
  (stop [this] this)
  RouteProvider
  (routes [this] (:api this)))

(defn new-api-service [& {:as opts}]
  (-> (->> opts
        (merge {})
        (s/validate {})
        map->ApiService)
    (using [:database])))
