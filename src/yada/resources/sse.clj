;; Copyright © 2015, JUXT LTD.

(ns yada.resources.sse
  (:require
   [clojure.core.async :refer [chan mult tap]]
   [manifold.stream :refer [->source transform]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   clojure.core.async.impl.channels
   clojure.core.async.impl.protocols
   manifold.stream.async)
  (import (clojure.core.async.impl.protocols ReadPort)))

(extend-protocol p/ResourceCoercion
  ReadPort
  (as-resource [ch]
    (let [mlt (mult ch)]
      {:produces [{:media-type "text/event-stream"
                   :charset charset/platform-charsets}]
       :methods {:get {:handler (fn [ctx]
                                  (let [ch (chan)]
                                    (tap mlt ch)
                                    (transform (map (partial format "data: %s\n\n")) (->source ch))))}}})))
