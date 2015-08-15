;; Copyright © 2015, JUXT LTD.

(ns yada.resources.collection-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer [Get GET]])
  (:import [clojure.lang APersistentMap]))

(defrecord MapResource [m last-modified]
  p/ResourceModification
  (last-modified [_ ctx] last-modified)

  p/ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
      :content-type #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
      :charset charset/platform-charsets}])

  Get
  (GET [_ ctx] m))

(extend-protocol p/ResourceCoercion
  APersistentMap
  (make-resource [m]
    (->MapResource m (to-date (now)))))
