;; Copyright © 2014-2017, JUXT LTD.

(ns yada.aleph
  (:require
   [aleph.http :as http]
   [yada.handler :refer [as-handler]]))

(defn listener
  "Start an HTTP listener on a given port. If not specified, listener
  will be started on any available port. Returns {:port port :close fn}"
  [routes & [aleph-options]]
  (let [server
        (http/start-server
         (as-handler routes)
         (merge aleph-options {:port (or (:port aleph-options) 0) :raw-stream? true}))]
    {:port (aleph.netty/port server)
     :close (fn [] (.close server))
     :server server}))

;; Alias
(def ^:deprecated server listener)
