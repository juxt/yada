;; Copyright © 2015, JUXT LTD.

(ns selfie.system
  (:require
   [aleph.http :as http]
   [bidi.bidi :as bidi]
   [bidi.ring :refer [make-handler]]
   [com.stuartsierra.component :refer [system-map Lifecycle system-using using]]
   [selfie.api :refer [new-api-component]]
   [schema.core :as s]
   [yada.yada :refer [yada]]))

(defn create-routes [api]
  ["" [["/" (fn [req] {:body "Selfie"})]
       (bidi/routes api)
       [true (yada nil)]]])

(defrecord ServerComponent [api port]
  Lifecycle
  (start [component]
    (let [routes (create-routes api)]
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
   :api (new-api-component)
   :server (new-server-component config)))

(defn new-dependency-map []
  {:api {}
   :server [:api]})

(defn new-co-dependency-map []
  {:api {:server :server}})

(s/defschema UserPort (s/both s/Int (s/pred #(<= 1024 % 65535))))

(s/defn new-selfie-app [config :- {:port UserPort}]
  (-> (new-system-map config)
      (system-using (new-dependency-map))))
