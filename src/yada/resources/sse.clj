;; Copyright © 2015, JUXT LTD.

(ns yada.resources.sse
  (:require
   [clojure.core.async :refer [chan mult tap]]
   [manifold.stream :refer [->source transform]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer [Get]]
   clojure.core.async.impl.channels
   clojure.core.async.impl.protocols
   manifold.stream.async)
  (import (clojure.core.async.impl.protocols ReadPort)))

(defrecord ChannelResource [mult]
  p/ResourceRepresentations
  (representations [_]
    [{:content-type "text/event-stream"
      :charset charset/platform-charsets}])

  Get
  (GET [_ _]
    (let [ch (chan)]
      (tap mult ch)
      (transform (map (partial format "data: %s\n\n")) (->source ch)))))

(extend-protocol p/ResourceCoercion
  ReadPort
  (make-resource [ch]
    (->ChannelResource (mult ch))))
