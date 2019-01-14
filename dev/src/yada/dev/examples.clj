(ns yada.dev.examples
  (:require
   [yada.yada :as yada]
   [manifold.stream :as ms]
   [manifold.deferred :as d]
   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(defn routes []
  ["/examples"
   [
    ;; Server Sent Events
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
                       sink))}}})]

    ;; Authentication/Authorization
    ["/auth"
     [
      ;; tag::basic[]
      ["/basic"
       (yada/resource
        {:authentication  ;; <1>
         {:scheme "Basic" ;; <2>
          :authenticate
          (fn [ctx [user password] _]
            (when (not (str/blank? user)) ;; <3>
              {:user user}))              ;; <4>
          :realm "WallyWorld"             ;; <5>
          }

         :methods
         {:get
          {:produces {:media-type "text/plain"
                      :charset "UTF-8"}
           :response
           (fn [ctx]
             (str "Welcome " (get-in ctx [:credentials :user])) ;; <6>
             )}}})]
      ;; end::basic[]
      ]]]])
