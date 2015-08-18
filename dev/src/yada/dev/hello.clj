;; Copyright © 2015, JUXT LTD.

(ns yada.dev.hello
  (:require
   [clojure.core.async :refer (chan go <! >! timeout go-loop)]
   [com.stuartsierra.component :refer [Lifecycle]]
   [bidi.bidi :refer [RouteProvider]]
   [yada.swagger :refer [swaggered]]
   yada.resources.sse
   [yada.yada :as yada]))

(defn hello []
  (yada/resource "Hello World!\n"))

(defn hello-atom []
  (yada/resource (atom "Hello World!\n")))

(defn hello-api []
  (swaggered {:info {:title "Hello World!"
                     :version "1.0"
                     :description "Demonstrating yada + swagger"}
              :basePath "/hello-api"
              }
             ["/hello" (hello)]))

(defn hello-atom-api []
  (swaggered {:info {:title "Hello World!"
                     :version "1.0"
                     :description "Demonstrating yada + swagger"}
              :basePath "/hello-api"
              }
             ["/hello" (hello-atom)]))

(defn hello-sse [ch]
  (go-loop [t 0]
    (when (>! ch (format "Hello World! (%d)" t))
      (<! (timeout 100))
      (recur (inc t))))
  (yada/resource ch))

(defrecord HelloWorldExample [channel]
  Lifecycle
  (start [component]
    (assoc component :channel (chan 10)))

  (stop [component] component)

  RouteProvider
  (routes [_]
    [""
     [["/hello" (hello)]
      ["/hello-atom" (hello-atom)]

      ;; Swagger
      ["/hello-api" (hello-api)]
      ["/hello-atom-api" (hello-atom-api)]

      ;; Realtime
      ["/hello-sse" (hello-sse channel)]]]))

(defn new-hello-world-example [& {:as opts}]
  (map->HelloWorldExample opts))
