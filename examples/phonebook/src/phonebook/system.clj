;; Copyright © 2015, JUXT LTD.

(ns phonebook.system
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring :refer [make-handler]]
   [com.stuartsierra.component :refer [system-map Lifecycle]]
   [aleph.http :as http]
   [phonebook.db :refer [create-db]]
   [phonebook.api :refer [new-api-component]]
   [yada.yada :refer [yada]]))

(defn create-routes [api]
  ["" [["/" (fn [req] {:body "Phonebook"})]
       (bidi/routes api)
       [true (yada nil)]]])

(defrecord ServerComponent [api]
  Lifecycle
  (start [component]
    (let [routes (create-routes api)]
      (assoc component
             :routes routes
             :server (http/start-server (make-handler routes) {:port 8099}))))
  (stop [component]
    (when-let [server (:server component)]
      (.close server))
    (dissoc component :server)))

(defn new-server-component []
  (map->ServerComponent {}))

(defn new-system-map []
  (system-map
   :atom-db (create-db
             {1 {:surname "Sparks"
                 :firstname "Malcolm"
                 :phone "1234"}
              2 {:surname "Pither"
                 :firstname "Jon"
                 :phone "1235"}})
   :api (new-api-component)
   :server (new-server-component)))

(defn new-dependency-map []
  {:api {:db :atom-db}
   :server [:api]})

(defn new-co-dependency-map []
  {:api {:server :server}})
