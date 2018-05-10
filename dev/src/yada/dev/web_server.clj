;; Copyright © 2014-2017, JUXT LTD.

(ns yada.dev.web-server
  (:require
   [com.stuartsierra.component :refer [Lifecycle using]]
   [clojure.tools.logging :refer :all]

   ;; How to switch based on profile?
   [yada.yada :as yada]

   [yada.dev.manual :as manual]
   [yada.dev.examples :as examples]
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

                 ["/" (yada/redirect ::manual/index)]

                 (manual/routes)
                 (examples/routes)

                 [["/specs/rfc" :rfcnum]
                  (yada/resource
                   {:parameters {:path {:rfcnum String}}

                    :properties
                    (fn [ctx]
                      (let [f (io/file "dev/resources/spec" (format "rfc%s.html" (-> ctx :parameters :path :rfcnum)))]
                        {:exists? (.exists f)
                         :last-modified (.lastModified f)
                         ::file f}))

                    :methods
                    {:get {:produces "text/html"
                           :response (fn [ctx]
                                       (-> ctx :properties ::file))}}})]

                 ["/" (yada/handler (io/file "dev/resources/static"))]])

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
