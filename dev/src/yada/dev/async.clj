(ns yada.dev.async
  (:require
   [clojure.core.async :refer (go go-loop timeout <! >! chan)]
   [manifold.stream :refer (->source)]))

(defn new-handler []
  (fn [req]
    {:status 200
     :headers {"content-type" "text/event-stream"
               "x-red" "foo"}
     :body (let [ch (chan 10)]
             (go-loop []
               (println "looping")
               (>! ch "Hello\n")
               (>! ch "Right\n")
               (>! ch "Great\n")
               (<! (timeout 1000))
               (recur)
               )
             (->source ch)
             )}))
