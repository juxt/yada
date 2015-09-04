;; Copyright © 2015, JUXT LTD.

(ns yada.dev.cors-demo
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer (html)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]])
  (:import [modular.bidi Router]))

(defn index [{:keys [*router templater]}]
  (yada
   (new-template-resource
    "templates/page.html"
    {:content (html
               [:div.container
                [:h2 "CORS demo"]])
     :scripts ["ajax.js"]})
   {:id ::index}))

(s/defrecord CorsDemo [*router :- (co-dep Router)
                       config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      ["ajax.js" (yada (new-template-resource "cors/ajax.js" {:origin (config/docsite-origin config)}))]
      ["talk/" (yada (io/file "/home/malcolm/src/thi.ng/talks/we-are-the-incanters"))]
      ]]))

(defn new-cors-demo [config]
  (-> (map->CorsDemo {:config config})
      (co-using [:router])))
