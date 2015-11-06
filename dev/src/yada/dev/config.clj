;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :refer (read-config)]
   [phonebook.db :refer [Phonebook]]))

(s/defschema UserPort (s/both s/Int (s/pred #(<= 1024 % 65535))))

(s/defschema ConfigSchema
  {:prefix s/Str
   :ports {:docsite UserPort
           :console UserPort
           :cors-demo UserPort
           :talks UserPort
           }
   :phonebook {:port UserPort
               :entries Phonebook}})

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config
   "dev/config.edn"
   {:profile profile
    :schema ConfigSchema}))

(defn docsite-port [config]
  (-> config :ports :docsite))

(defn console-port [config]
  (-> config :ports :console))

(defn cors-demo-port [config]
  (-> config :ports :cors-demo))

(defn talks-port [config]
  (-> config :ports :talks))

(defn phonebook-port [config]
  (-> config :phonebook :port))

(defn docsite-origin [config]
  (str (:prefix config)
       (when-let [port (docsite-port config)]
         (str ":" port))))

(defn cors-demo-origin [config]
  (str (:prefix config)
       (when-let [port (cors-demo-port config)]
         (str ":" port))))

(defn talks-origin [config]
  (str (:prefix config)
       (when-let [port (talks-port config)]
         (str ":" port))))

(defn phonebook-origin [config]
  (str (:prefix config)
       (when-let [port (phonebook-port config)]
         (str ":" port))))
