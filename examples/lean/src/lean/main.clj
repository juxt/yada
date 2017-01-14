;; Copyright © 2014-2017, JUXT LTD.

(ns lean.main
  (:require [yada.yada :refer [resource listener]]))

(def svr
  (listener
   ["/" (resource
         {:methods
          {:get
           {:produces "text/plain"
            :response "Hello World!"}}})]
   {:port 3000}))

;;((:close svr))
