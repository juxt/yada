;; Copyright © 2015, JUXT LTD.

(ns yada.dev.api
  (:require
   [com.stuartsierra.component :refer (Lifecycle using)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]
   [pets :as pets]
   [yada.swagger :refer (Handler ->DefaultAsyncHandler)]
   [yada.core :refer (make-async-handler)]))

(defrecord ApiService [database]
  Lifecycle
  (start [this] (assoc this :api (pets/pets-api database (->DefaultAsyncHandler))))
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
