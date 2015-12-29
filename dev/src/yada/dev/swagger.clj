(ns yada.dev.swagger
  (:require
   [clojure.java.io :as io]
   [bidi.bidi :refer [RouteProvider]]
   [yada.yada :refer [yada]]))

(defrecord PhonebookSwaggerUiIndex []
  RouteProvider
  (routes [_] ["/swagger-ui/phonebook-swagger.html"
               (yada (io/resource "swagger/phonebook-swagger.html"))]))

(defn new-phonebook-swagger-ui-index []
  (->PhonebookSwaggerUiIndex))

