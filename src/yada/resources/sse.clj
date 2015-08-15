;; Copyright © 2015, JUXT LTD.

(ns yada.resources.sse
  (:require
   [clojure.core.async :refer [chan mult tap]]
   [yada.resource :refer [ResourceRepresentations platform-charsets ResourceCoercion]]
   [yada.methods :refer [Get]]
   [manifold.stream :refer [->source transform]]
   clojure.core.async.impl.channels
   clojure.core.async.impl.protocols
   manifold.stream.async)
  (import (clojure.core.async.impl.protocols ReadPort)))

(defrecord ChannelResource [mult]
  ResourceRepresentations
  (representations [_]
    [{:content-type "text/event-stream"
      :charset platform-charsets}])

  Get
  (GET [_ _]
    (let [ch (chan)]
      (tap mult ch)
      (transform (map (partial format "data: %s\n\n")) (->source ch)))))

(extend-protocol ResourceCoercion
  ReadPort
  (make-resource [ch]
    (->ChannelResource (mult ch))))
