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
     [["console/home" (yada/resource (io/file "console/resources/static/index.html") {:id ::index})]
      ["react/react.min.js" (yada/resource (io/resource "cljsjs/production/react.min.inc.js"))]
      ["cljs" (files {:dir "target/cljs"})]

      ;; Customized css
      ["material.min.css" (yada/resource (io/file "console/resources/static/material.min.css"))]
      ["fonts.css" (yada/resource (io/file "console/resources/static/fonts.css"))]
      ["mdl.woff2" (yada/resource (io/file "console/resources/static/mdl.woff2"))]

      ["mdl" (resources-maybe {:prefix "META-INF/resources/webjars/material-design-lite/1.0.2"})]

      ]]))

#_(io/resource "META-INF/resources/webjars/material-design-lite/1.0.2/material.css")

(defn new-external-content [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->ExternalContent)
      (using [:templater])
      (co-using [:router])))
