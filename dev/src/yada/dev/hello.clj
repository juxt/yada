(ns yada.dev.hello
  (:require
   [yada.yada :refer (resource)]
   [yada.swagger :refer (swaggered)]
   [modular.ring :refer (WebRequestHandler)]))

(defrecord HelloApi []
  WebRequestHandler
  (request-handler [_]
    (swaggered {:info {:title "Hello World!"
                       :version "0.0.1"
                       :description "Demonstrating yada + swagger"}
                :basePath ""
                }
               ["/hello" (resource "Hello World!\n")])))

(defn new-hello-api [& {:as opts}]
  (->HelloApi))
