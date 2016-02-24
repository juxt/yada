;; Copyright © 2015, JUXT LTD.

(ns yada.test
  (:require
   [byte-streams :as b]
   [bidi.ring :as br]
   [bidi.vhosts :as bv]
   [yada.handler :refer [handler]]
   [yada.resource :refer [resource]])
  (:import
   [bidi.vhosts VHostsModel]
   [clojure.lang PersistentVector APersistentMap]
   [yada.handler Handler]
   [yada.resource Resource]))

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

(defprotocol HandlerCoercion
  (as-handler [_] "Coerce to a handler"))

(extend-protocol HandlerCoercion
  Handler
  (as-handler [this] this)
  Resource
  (as-handler [this] (handler this))
  APersistentMap
  (as-handler [route] (as-handler (resource route)))
  PersistentVector
  (as-handler [route] (br/make-handler route))
  VHostsModel
  (as-handler [model] (bv/make-handler model))
  Object
  (as-handler [this] (handler this)))

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


