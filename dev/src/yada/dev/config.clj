;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [aero.core :refer (read-config)]))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config "dev/config.edn" {:profile profile}))

(defn prefix [config]
  (str (:prefix config)
       (when-let [port (-> config :ports :docsite)]
         (str ":" port))))
