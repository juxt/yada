(ns yada.dev.hello
  (:require
   [com.stuartsierra.component :refer [Lifecycle]]
   [bidi.bidi :refer [RouteProvider]]
   [yada.swagger :refer [swaggered]]
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

(defrecord HelloWorldExample []
  RouteProvider
  (routes [_]
    [""
     [["/hello" (hello)]
      ["/hello-atom" (hello-atom)]

      ;; Swagger
      ["/hello-api" (hello-api)]
      ["/hello-atom-api" (hello-atom-api)]]]))

(defn new-hello-world-example [& {:as opts}]
  (->HelloWorldExample))
