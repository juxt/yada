;; Copyright © 2016, JUXT LTD.

(ns edge.main
  (:require [yada.resource :refer [resource]]))

(def svr
  (listener
   ["/" (resource
         {:methods
          {:get
           {:produces "text/plain"
            :response "Hello World"}}})]
   {:port 3000}))
