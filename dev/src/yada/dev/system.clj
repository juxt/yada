;; Copyright © 2014-2017, JUXT LTD.

(ns yada.dev.system
  "Components and their dependency relationships"
  (:refer-clojure :exclude [read])
  (:require
   [bidi.vhosts :refer [make-handler]]
   [clojure.java.io :as io]
   [clojure.tools.reader :refer [read]]
   [clojure.string :as str]
   [clojure.tools.reader.reader-types :refer [indexing-push-back-reader]]
   [com.stuartsierra.component :refer [system-map system-using using]]
   [yada.dev.config :as config]
   [yada.dev.web-server :refer [new-web-server]]))

(defn new-system-map
  [config]
  (system-map
   :web-server (new-web-server config)))

(defn new-dependency-map []
  {})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config/config :prod))
      (system-using (new-dependency-map))))
