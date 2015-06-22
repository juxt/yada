(ns yada.map-resource
  (:require
   [yada.resource :refer (Resource)]))

(defrecord MapResource [m]
  Resource
  )
