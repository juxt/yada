; Copyright © 2015, JUXT LTD.

(ns yada.walk
  (:require
   [yada.core :refer (resource)]
   [clojure.walk :refer (postwalk)])
  (:import [yada.core HttpResource]))

;; Functions to update inner routes

(defn update-routes [routes f & args]
  (postwalk
   (fn [x]
     (if (instance? HttpResource x)
       (apply f x args)
       x))
   routes))

(defn merge-options [other-options routes]
  (update-routes routes
                 (fn [{:keys [base options]}]
                   ;; Recreate the resource from the base, but with new options
                   (resource base (merge options other-options)))))

(defn basic-auth [realm auth-fn routes]
  (merge-options
   {:security {:type :basic :realm realm}
    :authorization auth-fn}
   routes))
