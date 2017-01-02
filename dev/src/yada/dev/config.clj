;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :as aero]))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (aero/read-config "dev/config.edn" {:profile profile}))

(defn get-listener-port [config]
  (get-in config [:listener :port]))

(defn get-host [config]
  (get-in config [:listener :host]))
