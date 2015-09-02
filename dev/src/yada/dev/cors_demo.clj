;; Copyright © 2015, JUXT LTD.

(ns yada.dev.cors-demo
  (:require
   [clojure.java.io :as io]
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [com.stuartsierra.component :refer [using Lifecycle]]
   [hiccup.core :refer (html)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.template :as template :refer (render-template)]
   [schema.core :as s]
   [yada.dev.template :refer [new-template-resource]]
   [yada.yada :as yada :refer [yada]]))

(defn index [{:keys [*router templater]}]
  (yada
   (fn [ctx]
     ;; TODO: Replace with template resource
     (render-template
      templater
      "templates/page.html.mustache"
      {:content
       (html
        [:div.container
         [:h2 "CORS demo"]
         ])}))
   {:id ::index
    :representations [{:media-type "text/html"
                       :charset "utf-8"}]}))

(defrecord CorsDemo [*router templater template]
  Lifecycle
  (start [component] (assoc component :template (new-template-resource "cors/ajax.js2" {:origin "ABC"})))
  (stop [component] component)

  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      ["ajax.js" (yada template)]
      ]]))

(defn new-cors-demo [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->CorsDemo)
      (using [:templater])
      (co-using [:router])))
