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
   [yada.yada :refer [yada] :as yada]))

(defn selfie-index-resource []
  (yada/resource
   {:properties {}
    :methods
    {:get {:response (fn [ctx] "Index")}
     :post {:response (fn [ctx]
                       (throw (ex-info "TODO: body should be accessible somewhere"
                                       {:request (:request ctx)})))}}}))

(defn api []
  ["" [["/selfie" (yada (selfie-index-resource))]]])

(s/defrecord ApiComponent []
  RouteProvider
  (routes [_] (api)))

(defn new-api-component []
  (map->ApiComponent {}))
