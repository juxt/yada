(ns yada.dev.hello
  (:require
   [yada.yada :as yada]
   [yada.swagger :refer (swaggered)]
   [bidi.bidi :refer (RouteProvider)]
   ))

(def hello
  (yada/resource "Hello World!\n"))

(def mutable-hello
  (yada/resource (atom "Hello World!\n")))

(defrecord HelloWorldExample []
  RouteProvider
  (routes [_]
    [""
     [["/hello" hello]

      ["/mutable-hello" mutable-hello]

      ;; Swagger
      ["/hello-api"
       (swaggered {:info {:title "Hello World!"
                          :version "1.0"
                          :description "Demonstrating yada + swagger"}
                   :basePath "/hello-api"
                   }
                  ["/hello" hello])]

      ["/mutable-hello-api"
       (swaggered {:info {:title "Hello World!"
                          :version "1.0"
                          :description "Demonstrating yada + swagger"}
                   :basePath "/hello-api"
                   }
                  ["/hello" mutable-hello])]]]))

(defn new-hello-world-example [& {:as opts}]
  (->HelloWorldExample))
