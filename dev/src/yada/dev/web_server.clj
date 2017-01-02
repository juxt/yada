;; Copyright © 2016, JUXT LTD.

(ns yada.dev.web-server
  (:require
   [com.stuartsierra.component :refer [Lifecycle using]]
   [clojure.tools.logging :refer :all]

   ;; How to switch based on profile?
   [yada.yada :as yada]

   [yada.dev.docsite :as docsite]
   [yada.dev.manual :as manual]
   [yada.dev.config :as config]
   [bidi.vhosts :refer [vhosts-model]]
   [clojure.java.io :as io]))

(defrecord WebServer [config listener]
  Lifecycle
  (start [component]
    (infof "Starting webserver...")
    (if listener
      component                         ; idempotence
      (if-let [port (config/get-listener-port config)]
        (let [listener
              (yada/listener
               (vhosts-model
                [(config/get-host config)
                 #_["/index.html" (docsite/index)]

                 (manual/routes)
                 ["/" (yada/handler (io/file "dev/resources/static"))]

                 ])
               {:port port})]
          (infof "Started web-server on port %s" (:port listener))
          (assoc component :listener listener))
        component)))

  (stop [component]
    (when-let [close (get-in component [:listener :close])]
      (close))
    (assoc component :listener nil)))

(defn new-web-server [config]
  (using
   (map->WebServer {:config config})
   []))
