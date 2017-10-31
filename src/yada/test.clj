;; Copyright © 2014-2017, JUXT LTD.

(ns yada.test
  (:require
   [byte-streams :as b]
   [yada.handler :refer [as-handler]]
   [bidi.vhosts :refer [vhosts-model]]
   [yada.aleph :as aleph]))

(defn request-for [method uri options]
  (let [uri (new java.net.URI uri)]
    (merge
     {:server-port 80
      :server-name "localhost"
      :remote-addr "localhost"
      :uri (.getPath uri)
      :query-string (.getRawQuery uri)
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

(defmacro with-aleph
  "Runs resource in aleph and defines url for use in body"
  [url-bind resource & body]
  `(let [resource# ~resource
         vmodel# (vhosts-model [:* ["/" resource#]])
         listener# (aleph/listener vmodel#)
         port# (:port listener#)
         close# (:close listener#)
         ~url-bind (str "http://localhost:" port#)]
     (try
       ~@body
       (finally (close#)))))
