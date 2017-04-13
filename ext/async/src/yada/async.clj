;; Copyright © 2014-2017, JUXT LTD.

(ns yada.async
  (:require
   [clojure.core.async :as a]
   [manifold.stream.async]
   [yada.body :refer [MessageBody to-body render-seq]])
  (:import
   [clojure.core.async Mult]
   [clojure.core.async.impl.channels ManyToManyChannel]
   [manifold.stream.async CoreAsyncSource]))

(extend-protocol MessageBody
  Mult
  (to-body [mlt representation]
    (let [ch (a/chan 10)]
      (a/tap mlt ch)
      (to-body ch representation)))
  (content-length [_] nil)

  ManyToManyChannel
  (to-body [ch representation] (render-seq ch representation))
  (content-length [_] nil))
