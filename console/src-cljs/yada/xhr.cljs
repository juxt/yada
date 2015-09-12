(ns yada.xhr
  (:require
   [goog.events :as events]
   [goog.net.XhrIo :as xhr]
   [cljs.reader :as reader]
   goog.net.EventType)
  )

(def GET "GET")
(def PUT "PUT")
(def POST "POST")
(def DELETE "DELETE")

(defn request [method uri callback]
  (doto (new goog.net.XhrIo)
    (events/listen goog.net.EventType/SUCCESS
                   (fn [ev]
                     (let [xhrio (.-target ev)
                           status (.getStatus xhrio)]
                       (callback status (.getResponseText xhrio)))))

    ;;(.setTimeoutInterval (:timeout m))
    (.send uri
           method
           nil {}
           #_{"Accept" "application/edn"}))
  )


(defn foo []
  (GET "http://localhost:8090/journal/eeeda318-d95c-4342-942f-8be9e87c44a9"))
