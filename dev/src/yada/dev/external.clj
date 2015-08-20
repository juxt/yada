;; Copyright © 2015, JUXT LTD.

(ns yada.dev.external
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [com.stuartsierra.component :refer (using)]
   [modular.component.co-dependency :refer (co-using)]
   [schema.core :as s]
   [yada.yada :as yada]))

(defn index [{:keys [*router templater]}]
  (yada/resource "External Server (webapp)"
                 {:id ::index
                  :representations [{:media-type #{"text/html"}
                                     :charset #{"utf-8"}
                                     }]}))

(defrecord ExternalContent [*router templater]
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      ]]))

(defn new-external-content [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->ExternalContent)
      (using [:templater])
      (co-using [:router])))
