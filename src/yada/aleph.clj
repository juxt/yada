;; Copyright © 2015, JUXT LTD.

(ns yada.aleph
  (:require
   [aleph.http :as http]
   [bidi.ring :as br]
   [yada.handler :refer [as-handler]]))

(defn server [routes port]
  (http/start-server
   (as-handler routes)
   {:port port :raw-stream? true}))

