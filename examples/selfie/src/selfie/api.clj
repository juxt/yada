;; Copyright © 2015, JUXT LTD.

(ns selfie.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [selfie.www :refer [new-index-resource]]
   [yada.yada :refer [yada]]))

(defn api []
  ["/selfie" (yada (new-index-resource))])

(s/defrecord ApiComponent []
  RouteProvider
  (routes [_] (api)))

(defn new-api-component []
  (map->ApiComponent {}))
