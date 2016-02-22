;; Copyright © 2015, JUXT LTD.

(ns phonebook.system
  (:require
   [aleph.http :as http]
   [bidi.bidi :as bidi]
   [bidi.vhosts :refer [vhosts-model make-handler]]
   [com.stuartsierra.component :refer [system-map Lifecycle system-using using]]
   [phonebook.db :as db]
   [phonebook.api :refer [new-api-component]]
   [phonebook.schema :refer [Config]]
   [schema.core :as s]
   [yada.yada :refer [yada]]))

(defn create-vhosts-model [api]
  (vhosts-model
   [[{:scheme :http :host "localhost:8099"}]
    ["/" (fn [req] {:body "Phonebook"})]
    (:routes api)
    [true (yada "hi")] ; TODO: uncomment this when pure-data branch done
    ]))

(defrecord ServerComponent [api port]
  Lifecycle
  (start [component]
    (let [model (create-vhosts-model api)]
      (assoc component
             :vhosts-model model
             :server (http/start-server (make-handler model) {:port port :raw-stream? true}))))
  (stop [component]
    (when-let [server (:server component)]
      (.close server))
    (dissoc component :server)))

(defn new-server-component [config]
  (map->ServerComponent config))

(defn new-system-map [config]
  (system-map
   :atom-db (db/create-db (:entries config))
   :api (new-api-component)
   :server (new-server-component config)))

(defn new-dependency-map []
  {:api {:db :atom-db}
   :server [:api]})

(s/defn new-phonebook [config :- Config]
  (-> (new-system-map config)
      (system-using (new-dependency-map))))
