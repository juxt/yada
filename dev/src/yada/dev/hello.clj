;; Copyright © 2015, JUXT LTD.

(ns yada.dev.hello
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.core.async :refer (chan go <! >! timeout go-loop)]
   [com.stuartsierra.component :refer [Lifecycle]]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.swagger :refer [swaggered]]
   [yada.yada :as yada :refer [yada]]))

(defn hello []
  (yada "Hello World!\n" {:error-handler identity}))

(defn hello-atom []
  (yada (atom "Hello World!\n") {:error-handler identity}))

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
  (yada/yada ch))

(defn hello-different-origin-1 [config]
  ;; TODO: Replace with {:error-handler nil} and have implementation
  ;; check with contains? for key
  (yada "Hello World!\n" {:error-handler identity
                          :access-control {:allow-origin (config/cors-demo-origin config)
                                           :allow-headers #{"authorization"}}
                          :representations [{:media-type "text/plain"}]}))

(defn hello-different-origin-2 [config]
  (yada "Hello World!\n" {:error-handler identity
                          :access-control {:allow-origin true ; only show incoming origin
                                           :allow-credentials true}
                          :representations [{:media-type "text/plain"}]}))

(defn hello-different-origin-3 [config]
  (yada "Hello World!\n" {:error-handler identity
                          :access-control {:allow-origin "*"
                                           :allow-credentials true}
                          :representations [{:media-type "text/plain"}]}))

(s/defrecord HelloWorldExample [channel
                                config :- config/ConfigSchema]
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
      ["/hello-sse" (hello-sse channel)]

      ;; Remote access
      ["/hello-different-origin/1" (hello-different-origin-1 config)]
      ["/hello-different-origin/2" (hello-different-origin-2 config)]
      ["/hello-different-origin/3" (hello-different-origin-3 config)]
      ]]))

(defn new-hello-world-example [config]
  (map->HelloWorldExample {:config config}))
