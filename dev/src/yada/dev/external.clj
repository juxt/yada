;; Copyright © 2015, JUXT LTD.

(ns yada.dev.external
  (:require
   [bidi.bidi :refer (RouteProvider)]
   [bidi.ring :refer (files resources-maybe)]
   [com.stuartsierra.component :refer (using)]
   [modular.component.co-dependency :refer (co-using)]
   [clojure.java.io :as io]
   [schema.core :as s]
   [yada.yada :as yada]))

(defrecord ExternalContent [*router templater]
  RouteProvider
  (routes [component]
    ["/"
     [["console/home" (-> "console/resources/static/index.html" io/file (yada/resource {:id ::index}))]
      ["react/react.min.js" (-> "cljsjs/production/react.min.inc.js" io/resource yada/resource)]
      ["cljs" (files {:dir "target/cljs"})]

      ;; Customized css
      ["material.min.css" (-> "console/resources/static/material.min.css" io/file yada/resource)]
      ["fonts.css" (-> "console/resources/static/fonts.css" io/file yada/resource)]
      ["mdl.woff2" (-> "console/resources/static/mdl.woff2" io/file yada/resource)]

      ["mdl/" (resources-maybe {:prefix "META-INF/resources/webjars/material-design-lite/1.0.2/"})]

      ]]))

(defn new-external-content [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->ExternalContent)
      (using [:templater])
      (co-using [:router])))
