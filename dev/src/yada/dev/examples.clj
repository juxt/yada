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
     [["/basic"
       (yada/resource
        {:methods {:get {:produces "text/html"
                         :response (fn [ctx] (str "Welcome " (get-in ctx [:credentials :user])))}}
         :authentication {:scheme "Basic"
                          :authenticate (fn [ctx [user password] _]
                                          (when (not (str/blank? user))
                                            (future {:user user})))
                          :realm "WallyWorld"}
         :authorize (fn [ctx creds]
                      (log/infof "authorize, creds is %s" creds)
                      (:user creds)
                      )})]]]]])
