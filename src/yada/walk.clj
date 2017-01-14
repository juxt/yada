;; Copyright © 2014-2017, JUXT LTD.

(ns yada.walk
  (:require
   [clojure.walk :refer [postwalk]])
  (:import [yada.handler Handler]
           [yada.resource Resource]))

;; Functions to update inner routes

(defn update-routes [routes f & args]
  (postwalk
   (fn [x]
     (if (or
          (instance? Handler x)
          (instance? Resource x))
       (apply f x args)
       x))
   routes))

(defn merge-options [m routes]
  (update-routes routes
                 (fn [{:keys [handler args]}]
                   (merge handler m))))

#_(defn basic-auth [routes realm authn-fn authorization ]
  (merge-options
   {:access-control
    {realm {:authentication-schemes
            [{:scheme "Basic"
              :verify authn-fn}]
            :authorization {:methods {:get "secret/view"}}}}}
   routes))
