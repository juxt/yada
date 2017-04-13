;; Copyright © 2016, JUXT LTD.

(ns lean.main
  (:require [yada.yada.lean :refer [resource listener]]))

(def svr
  (listener
   ["/" (resource
         {:methods
          {:get
           {:produces "text/plain"
            :response "Hello World!"}}})]
   {:port 3000}))

;;((:close svr))
