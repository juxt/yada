(ns yada.dev.external
  (:require
   [bidi.bidi :refer (RouteProvider tag)]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer (using)]
   [yada.dev.user-guide :refer (basename)]
   [yada.dev.examples :refer (make-handler get-path)]))

(defrecord ExternalResources [user-guide]
  RouteProvider
  (routes [component]
    ["/user-guide/examples"
     [["" (fn [req] {:body "examples"})]
      ["/"
       (vec
        (for [[_ h] (:examples user-guide)]
          [(get-path h) (make-handler h)]))]]]))

(defn new-external-resources [& {:as opts}]
  (->
   (->> opts
        (merge {})
        map->ExternalResources)
   (using [:user-guide])))
