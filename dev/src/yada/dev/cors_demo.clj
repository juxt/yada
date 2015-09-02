;; Copyright © 2015, JUXT LTD.

(ns yada.dev.cors-demo
  (:require
   [clojure.java.io :as io]
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer (html)]
   [modular.component.co-dependency :refer (co-using)]
   [schema.core :as s]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]]))

(defn index [{:keys [*router templater]}]
  (yada
   (new-template-resource "templates/page.html"
    {:content
     (html
      [:div.container
       [:h2 "CORS demo"]
       ])})
   {:id ::index}))

(defrecord CorsDemo [*router templater]
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      ["ajax.js" (yada (new-template-resource "cors/ajax.js" {:origin "ABC"}))]
      ]]))

(defn new-cors-demo [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->CorsDemo)
      (using [:templater])
      (co-using [:router])))
