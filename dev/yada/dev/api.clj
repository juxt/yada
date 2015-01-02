;; Copyright © 2015, JUXT LTD.

(ns yada.dev.api
  (:require
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler)]
   [bidi.bidi :refer (match-route)]
   [modular.bidi :refer (WebService)]
   [manifold.deferred :as d]
   [yada.core :refer (make-handler)]
   [yada.dev.database :refer (find-pets find-pet-by-id)]
   [pets :as pets]
   [schema.core :as s]))

(defn swagger-op-handlers [m]
  (into {} (for [[k v] m] [k (make-handler k v)])))

(defrecord ApiService [database]
  WebService
  (request-handlers [this]
    (swagger-op-handlers

     {pets/find-pets
      {:entity (fn [resource] (d/future (find-pets database)))
       :body {}}

      pets/add-pet {:body "addPet"}

      pets/find-pet-by-id
      {:find-resource (fn [opts] {:id (-> opts :params :id)})
       :entity (fn [{id :id}] (d/future (find-pet-by-id database id)))}}))

  (routes [this]
    pets/pets-spec)

  (uri-context [_] ""))

(def new-api-service-schema {})

(defn new-api-service [& {:as opts}]
  (-> (->> opts
        (merge {})
        (s/validate new-api-service-schema)
        map->ApiService)
    (component/using [:database])))
