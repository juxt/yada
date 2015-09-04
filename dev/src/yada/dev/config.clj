;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :refer (read-config)]))

(s/defschema UserPort (s/both s/Int (s/pred #(<= 1024 % 65535))))

(s/defschema ConfigSchema
  {:prefix s/Str
   :ports {:docsite UserPort
           :cors-demo UserPort}})

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config
   "dev/config.edn"
   {:profile profile
    :schema ConfigSchema}))

(defn docsite-origin [config]
  (str (:prefix config)
       (when-let [port (-> config :ports :docsite)]
         (str ":" port))))

(defn cors-demo-origin [config]
  (str (:prefix config)
       (when-let [port (-> config :ports :cors-demo)]
         (str ":" port))))
