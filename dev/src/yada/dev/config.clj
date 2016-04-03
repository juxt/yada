;; Copyright © 2015, JUXT LTD.

(ns yada.dev.config
  (:require
   [schema.core :as s]
   [aero.core :refer (read-config)]
   [phonebook.schema :refer [PhonebookEntry Phonebook UserPort]]))

(s/defschema ConfigSchema
  {:docsite {:port UserPort
             :vhosts [s/Str]}

   :phonebook {:vhosts [s/Str]
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



