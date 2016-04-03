;; Copyright © 2015 JUXT LTD.

(ns yada.dev.server
  (:require
   [aleph.http :as http]
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [bidi.vhosts :refer [make-handler vhosts-model uri-for]]
   [bidi.bidi :refer [routes RouteProvider]]
   [com.stuartsierra.component :refer [Lifecycle using]]))

(s/defrecord Webserver [config :- {:port s/Int
                                   s/Keyword s/Any}
                        vhosts
                        router :- (s/protocol RouteProvider)
                        server
                        phonebook]
  Lifecycle
  (start [component]
    (let [model (apply vhosts-model
                       (conj (-> phonebook :server :vhosts-model :vhosts)
                             [vhosts (routes router)]))]
      (try
        (assoc component
               :server (http/start-server
                        (make-handler model)
                        config)
               :config config)
        (catch Exception e
          (errorf e "Failed to start web-server")
          component))))
  
  (stop [component]
    (when-let [server (:server component)]
      (.close server))
    (dissoc component :server)))

(defn new-webserver [config vhosts]
  (using
   (map->Webserver {:config config
                    :vhosts vhosts})
   [:router :phonebook]))

