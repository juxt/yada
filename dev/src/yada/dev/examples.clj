(ns yada.dev.examples
  (:require
   [yada.yada :as yada]
   [manifold.stream :as ms]
   [manifold.deferred :as d]))

(defn routes []
  ["/examples"
   [
    ["/sse-resource" (yada/handler (ms/periodically 400 (fn [] "foo")))]
    ["/sse-body"
     (yada/resource
      {:methods
       {:get {:produces "text/event-stream"
              :response (fn [ctx]
                          (let [source (ms/periodically 400 (fn [] "foo"))
                                sink (ms/stream 10)]
                            (ms/on-closed
                             sink
                             (fn [] (println "closed")))
                            (ms/connect source sink)
                            sink
                            ))}}})]
    ["/sse-body-with-close"
     (yada/resource
      {:methods
       {:get
        {:produces "text/event-stream"
         :response (fn [ctx]
                     (let [source (ms/periodically 400 (fn [] "foo"))
                           sink (ms/stream 10)]
                       (ms/on-closed sink (fn [] (println "closed")))
                       (ms/connect source sink)
                       sink))}}})]]])
