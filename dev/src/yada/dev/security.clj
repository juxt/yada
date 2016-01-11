;; Copyright © 2015, JUXT LTD.

(ns yada.dev.security
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [schema.core :as s]
   [yada.yada :as yada :refer [yada resource as-resource]]
   [bidi.bidi :refer [tag]]))

(defn hello []
  (yada "Hello World!\n"))

(s/defrecord SecurityExamples []
  RouteProvider
  (routes [_]
    ["/security"
     [["/basic" (yada
                 (resource
                  (merge (into {} (as-resource "hello"))
                         {:access-control
                          {:realm "accounts"
                           :scheme "Basic"
                           :verify (fn [[user password]]
                                     (if (= [user password]
                                            ["scott" "tiger"])
                                       {:user "scott"}
                                       ;; Keep trying
                                       nil))
                           :authorized-methods {:get true}}})))]

      

      ]]))

(defn new-security-examples [config]
  (map->SecurityExamples {}))


