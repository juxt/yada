(ns yada.dev.examples
  (:require
   [yada.yada :as yada]
   [manifold.stream :as ms]))

(defn routes []
  ["/examples"
   [
    ["/sse-body" (yada/resource
             {:methods
              {:get {:produces "text/event-stream"
                     :response (fn [ctx] (ms/periodically 400 (fn [] "foo")))}}})]
    ["/sse-resource" (yada/handler (ms/periodically 400 (fn [] "foo")))]]])
