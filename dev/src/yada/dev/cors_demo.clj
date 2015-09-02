;; Copyright © 2015, JUXT LTD.

(ns yada.dev.cors-demo
  (:require
   [clojure.java.io :as io]
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer (html)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]])
  (:import [modular.bidi Router]))

(defn index [{:keys [*router templater]}]
  (yada
   (new-template-resource "templates/page.html"
    {:content
     (html
      [:div.container
       [:h2 "CORS demo"]
       ])})
   {:id ::index}))

(s/defrecord CorsDemo [*router :- (co-dep Router)]
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      ["ajax.js" (yada (new-template-resource "cors/ajax.js" {:origin "ABC"}))]
      ]]))

(defn new-cors-demo [& {:as opts}]
  (-> (map->CorsDemo opts)
      (co-using [:router])))
