;; Copyright © 2015, JUXT LTD.

(ns selfie.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.yada :refer [yada]]))

(defrecord SelfieIndexResource []
  p/Properties
  (properties [_] {:doc/description "POST your selfies here!"})

  m/Post
  (POST [_ ctx]
    (throw (ex-info "TODO" {:request (:request ctx)})))

  m/Get
  (GET [_ ctx] "Index"))

(defn api []
  ["" [["/selfie" (yada (->SelfieIndexResource))]]])

(s/defrecord ApiComponent []
  RouteProvider
  (routes [_] (api)))

(defn new-api-component []
  (map->ApiComponent {}))
