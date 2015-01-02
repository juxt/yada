;; Copyright © 2015, JUXT LTD.

(ns yada.dev.website
  (:require
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler request-handler)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]))

(defrecord Website []
  WebService
  (request-handlers [this]
    {:index (fn [req]
              {:status 200 :body "SWAGGER-UI"})})
  (routes [this] ["" {"/index" :index}])
  (uri-context [this] ""))

(def new-website-schema {})

(defn new-website [& {:as opts}]
  (component/using
   (->> opts
     (merge {})
     (s/validate new-website-schema)
     map->Website)
   []))
