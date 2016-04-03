;; Copyright © 2015, JUXT LTD.

(ns phonebook.system
  (:require
   [aleph.http :as http]
   [bidi.bidi :as bidi]
   [bidi.vhosts :refer [vhosts-model make-handler]]
   [com.stuartsierra.component :refer [system-map Lifecycle system-using using]]
   [hiccup.core :refer [html]]
   [phonebook.db :as db]
   [phonebook.api :refer [new-api-component]]
   [phonebook.schema :refer [Config]]
   [schema.core :as s]
   [yada.yada :refer [handler server]]))

(defn create-vhosts-model [vhosts api]
  (vhosts-model
   [vhosts
    ["/" (fn [req] {:body (html [:p [:a {:href "/phonebook"} "Phonebook"]])})]
    (:routes api)
    [true (handler nil)]
    ]))

(defrecord ServerComponent [vhosts api port]
  Lifecycle
  (start [component]
    (let [model (create-vhosts-model vhosts api)]
      (assoc component
             :vhosts-model model
             :server (server model {:port port}))))
  (stop [component]
    (when-let [close (some-> component :server :close)]
      (close))
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
