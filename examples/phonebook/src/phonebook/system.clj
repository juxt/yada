;; Copyright © 2015, JUXT LTD.

(ns phonebook.system
  (:require
   [aleph.http :as http]
   [bidi.bidi :as bidi]
   [bidi.ring :refer [make-handler]]
   [com.stuartsierra.component :refer [system-map Lifecycle system-using using]]
   [phonebook.db :as db]
   [phonebook.api :refer [new-api-component]]
   [phonebook.schema :refer [Config]]
   [schema.core :as s]
   [yada.yada :refer [yada]]))

(defn create-routes [api]
  ["" [["/" (fn [req] {:body "Phonebook"})]
       (:routes api)
       [true (yada nil)] ; TODO: uncomment this when pure-data branch done
       ]])

(defrecord ServerComponent [api port]
  Lifecycle
  (start [component]
    (let [routes (create-routes api)]
      ;; We promised a dependency to tell it what the final routes are
      (deliver (:promise api) routes)
      (assoc component
             :routes routes
             :server (http/start-server (make-handler routes) {:port port :raw-stream? true}))))
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
