(ns ecto.website
  (:require
   [clojure.pprint :refer (pprint)]
   [modular.ring :refer (WebRequestHandler)]
   [manifold.deferred :as d]))

(defn index [req]
  (d/let-flow
   [status (d/future 200)]
   {:status status :body "foo"}))

(defrecord Website []
  WebRequestHandler
  (request-handler [this] index))

(defn new-website []
  (->Website))
