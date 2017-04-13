;; Copyright © 2015, JUXT LTD.

(ns yada.test
  (:require
   [byte-streams :as b]
   [bidi.ring :as br]
   [bidi.vhosts :as bv]
   [yada.handler :refer [handler as-handler]]
   [yada.resource :refer [resource]]))

(defn request-for [method uri options]
  (let [uri (new java.net.URI uri)]
    (merge
     {:server-port 80
      :server-name "localhost"
      :remote-addr "localhost"
      :uri (.getPath uri)
      :query-string (.getQuery uri)
      :scheme :http
      :request-method method}
     (cond-> options
             (:body options) (update :body b/to-byte-buffers)
             true (update :headers #(merge {"host" "localhost"} %))))))

(defn response-for
  "Produces yada responses for a number of types. Maps are interpreted
  as resource-maps, vectors are bidi routes. If you want these as
  bodies, use (response-for {:response …})"
  ([o]
   (response-for o :get "/" {}))
  ([o method]
   (response-for o method "/" {}))
  ([o method uri]
   (response-for o method uri {}))
  ([o method uri options]
   (let [h (as-handler o)
         response @(h (request-for method uri options))]
     (cond-> response
       (:body response) (update :body b/to-string)))))


