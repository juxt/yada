;; Copyright © 2015, JUXT LTD.

(ns yada.dev.external
  (:require
   [bidi.bidi :refer (RouteProvider tag)]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer (using)]
   [yada.dev.user-guide :refer (basename)]
   [yada.dev.examples :refer (make-example-handler get-path)]))

(defrecord ExternalResources [user-guide]
  RouteProvider
  (routes [component]
    ["/user-guide/examples"
     [["" (fn [req] {:body "examples"})]
      ["/"
       (vec
        (for [[_ ex] (:examples user-guide)]
          [(get-path ex)
           (make-example-handler ex)]))]]]))

(defn new-external-resources
  "External resources are those that run on a different port, such that
  they are considered to be from a different origin, from a CORS
  perspective. This allows testing of CORS functionality."
  [& {:as opts}]
  (->
   (->> opts
        (merge {})
        map->ExternalResources)
   (using [:user-guide])))
