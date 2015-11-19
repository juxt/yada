;; Copyright © 2015, JUXT LTD.

(ns selfie.api
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.yada :refer [yada]]))

(defrecord SelfieIndexResource []
  p/Properties
  (properties [_] {:doc/description "POST your selfies here!"})

  m/Post
  (POST [_ ctx]
    (throw (ex-info "TODO: body should be accessible somewhere" {:request (:request ctx)})))

  m/Get
  (GET [_ ctx] "Index"))

;; TODO: Note, I think that request content-types need to be support on a resource-by-resource basis rather than globally. Think about this. It's analogous to known-methods and allowed-methods. See swagger's consumes. This is an ideal place to use it!

(defn api []
  ["" [["/selfie" (yada (->SelfieIndexResource)
                        ;; TODO: what's a better name for this?
                        {:body-receiver nil}
                        )]]])

(s/defrecord ApiComponent []
  RouteProvider
  (routes [_] (api)))

(defn new-api-component []
  (map->ApiComponent {}))
