;; Copyright © 2015, JUXT LTD.

(ns yada.dev.console
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [com.stuartsierra.component :refer (using)]
   [modular.component.co-dependency :refer (co-using)]
   [clojure.java.io :as io]
   [schema.core :as s]
   [yada.yada :as yada :refer [yada]]))

(defrecord Console [config *router]
  RouteProvider
  (routes [component]
    ["/"
     [[["console/" [#".*" :path]] (-> "console/resources/static/index.html" io/file (yada {:id ::index}))]
      ["cljs" (files {:dir "target/cljs"})]

      #_["react/react-with-addons.min.js" (-> "cljsjs/development/react-with-addons.inc.js" io/resource yada)]
      ["react/react.min.js" (-> "cljsjs/react/production/react.min.inc.js" io/resource yada)]

      ["mdl/" (resources-maybe {:prefix "META-INF/resources/webjars/material-design-lite/1.0.2/"})]

      ["" (-> "console/resources/static/" io/file yada)]

      ]]))

(defn new-console [config]
  (-> (map->Console config)
      (co-using [:router])))


;;(io/resource "cljsjs/development/react-with-addons.inc.js")
