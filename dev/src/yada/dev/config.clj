;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :refer (read-config)]
   [phonebook.schema :refer [PhonebookEntry Phonebook UserPort]]))

(s/defschema ConfigSchema
  {:proxy? s/Bool

   :docsite {:scheme (s/enum "http" "https")
             :host s/Str
             :port UserPort}

   :phonebook {:scheme (s/enum "http" "https")
               :host s/Str
               :port UserPort
               :entries Phonebook}})

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config
   "dev/config.edn"
   {:profile profile
    :schema ConfigSchema}))

(defn port [config section]
  (get-in config [section :port]))

(defn origin [config section]
  (let [proxy? (:proxy? config)
        config (get config section)]
    (str (:scheme config)
         "://"
         (:host config)
         (when-not proxy?
           (str ":" (:port config))))))

(defn host [config section]
  (let [proxy? (:proxy? config)
        config (get config section)]
    (str (:host config)
         (when-not proxy?
           (str ":" (:port config))))))
