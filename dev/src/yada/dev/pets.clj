;; Copyright © 2015, JUXT LTD.

(ns yada.dev.pets
  (:require
   [com.stuartsierra.component :refer (Lifecycle using)]
   [bidi.bidi :refer (RouteProvider)]
   [schema.core :as s]
   [pets :as pets]
   [yada.swagger :refer (Handler ->DefaultAsyncHandler)]
   [yada.core :refer (make-async-handler)]))

(defrecord PetsApiService [database]
  Lifecycle
  (start [this] (assoc this
                       :api (pets/pets-api database (->DefaultAsyncHandler))))
  (stop [this] this)

  RouteProvider
  (routes [this] ["" (:api this)]))

(defn new-pets-api-service [& {:as opts}]
  (-> (->> opts
        (merge {})
        (s/validate {})
        map->PetsApiService)
    (using [:database])))
