;; Copyright © 2015, JUXT LTD.

(ns selfie.www
  (:require
   [bidi.bidi :refer [path-for]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.yada :as yada])
  (:import [manifold.stream.core IEventSource]))

(defrecord SelfieIndexResource []
  p/Properties
  (properties [_]
    {})

  m/Post
  (POST [_ ctx]
    (throw (ex-info "TODO" {})))

  m/Get
  (GET [_ ctx] "Index"))

(defn new-index-resource []
  (->SelfieIndexResource))
