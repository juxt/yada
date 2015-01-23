;; Copyright © 2015, JUXT LTD.

(ns yada.dev.swagger-ui
  (:require
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler request-handler)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]
   yada.swagger-v1
   [clojure.java.io :as io]
   [bidi.ring :as br]
   [hiccup.core :refer (html)]
   [cheshire.core :as json]
   clojure.pprint
   clojure.string
   ))

;; This ns only exists while we wait for a newer version than
;; [org.webjars/swagger-ui "2.0.24"] to appear on webjars.

(defrecord Website []
  WebService
  (request-handlers [this]
    {})
  (routes [this] ["" {"/swag" (br/files {:dir "/home/malcolm/src/swagger-ui/dist"})}])
  (uri-context [this] ""))

(def new-website-schema {})

(defn new-website [& {:as opts}]
  (component/using
   (->> opts
     (merge {})
     (s/validate new-website-schema)
     map->Website)
   []))
