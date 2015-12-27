;; Copyright © 2015, JUXT LTD.

(ns yada.dev.async
  (:require
   [bidi.bidi :refer [RouteProvider tag]]
   [com.stuartsierra.component :refer [Lifecycle]]
   [clojure.core.async :refer [go go-loop timeout <!! >!! chan close!] :as a]
   [yada.yada :refer [yada resource]]))

(defn new-handler [mlt]
  (yada
   (resource {:methods {:get {:produces "text/event-stream"
                              :response mlt}}})))

(defrecord SseExample []
  Lifecycle
  (start [component]
    (let [ch (chan 10)]
      (a/thread
        (when (>!! ch "Hello")
          (a/<!! (timeout 1000))
          (recur)))
      (assoc component :channel ch)))
  
  (stop [component]
    (when-let [ch (:channel component)]
      (close! ch))
    component)

  RouteProvider
  (routes [component]
    ["/sse" (-> (new-handler (a/mult (:channel component)))
                (tag ::sse-demo))]))

(defn new-sse-example []
  (map->SseExample {}))
