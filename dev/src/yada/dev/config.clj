;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :refer (read-config)]))

(s/defschema ConfigSchema
  {:prefix s/Str
   :ports {:docsite (s/maybe s/Int)
           :cors-demo (s/maybe s/Int)}})

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config
   "dev/config.edn"
   {:profile profile
    :schema ConfigSchema}))

(defn prefix [config]
  (str (:prefix config)
       (when-let [port (-> config :ports :docsite)]
         (str ":" port))))

(defn cors-prefix [config]
  (str (:prefix config)
       (when-let [port (-> config :ports :cors-demo)]
         (str ":" port))))
