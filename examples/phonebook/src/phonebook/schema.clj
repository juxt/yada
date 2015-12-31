(ns phonebook.schema
  (:require [schema.core :as s]))

(s/defschema PhonebookEntry {:surname String :firstname String :phone String})

(s/defschema Phonebook {s/Int PhonebookEntry})

(s/defschema UserPort (s/both s/Int (s/pred #(<= 1024 % 65535))))

(s/defschema Config
  {:scheme (s/enum "http" "https")
   :host s/Str
   :port UserPort
   :entries Phonebook})

