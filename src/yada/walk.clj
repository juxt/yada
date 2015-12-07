; Copyright © 2015, JUXT LTD.

(ns yada.walk
  (:require
   [yada.core :refer [yada]]
   [clojure.walk :refer [postwalk]])
  (:import [yada.handler Handler]))

;; Functions to update inner routes

(defn update-routes [routes f & args]
  (postwalk
   (fn [x]
     (if (instance? Handler x)
       (apply f x args)
       x))
   routes))

(defn merge-options [m routes]
  (update-routes routes
                 (fn [{:keys [handler args]}]
                   (merge handler m))))

(defn basic-auth [realm auth-fn routes]
  (merge-options
   {:security {:type :basic :realm realm}
    :authorization auth-fn}
   routes))
