;; Copyright © 2015, JUXT LTD.

(ns yada.aleph
  (:require
   [aleph.http :as http]
   [yada.handler :refer [as-handler]]))

(defn listener
  "Start an HTTP listener on a given port. If not specified, listener
  will be started on any available port. Returns {:port port :close fn}"
  [routes & [{:keys [port] :or {port 0} :as aleph-options}]]
  (let [server
        (http/start-server
         (as-handler routes)
         (merge {:port (or port 0) :raw-stream? true}
                aleph-options))]
    {:port (aleph.netty/port server)
     :close (fn [] (.close server))}))

;; Alias
(def ^:deprecated server listener)

