;; Copyright © 2015, JUXT LTD.

(ns yada.dev.api-v1
  (:require
   [com.stuartsierra.component :refer (Lifecycle using)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]
   [pets-v1 :as pets]
   ))

(defrecord ApiService [database]
  Lifecycle
  (start [this] (assoc this :api (pets/pets-api database)))
  (stop [this] this)
  WebService
  (request-handlers [this] {})
  (routes [this] (:api this))
  (uri-context [_] ""))

(defn new-api-service [& {:as opts}]
  (-> (->> opts
        (merge {})
        (s/validate {})
        map->ApiService)
    (using [:database])))
