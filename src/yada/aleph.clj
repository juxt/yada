;; Copyright © 2015, JUXT LTD.

(ns yada.aleph
  (:require
   [aleph.http :as http]
   [yada.handler :refer [as-handler]]))

(defn listener [routes & [{:keys [port] :or {port 3000} :as aleph-options}]]
  (http/start-server
   (as-handler routes)
   (merge {:port port
           :raw-stream? true} aleph-options)))

;; Alias
(def ^:deprecated server listener)
