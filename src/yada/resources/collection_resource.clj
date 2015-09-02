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
  p/ResourceProperties
  (resource-properties [_]
    {:representations
     [{:media-type
       #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
       :charset charset/platform-charsets}]})
  (resource-properties [_ ctx]
    {:last-modified last-modified})

  Get
  (GET [_ ctx] m))

(extend-protocol p/ResourceCoercion
  APersistentMap
  (as-resource [m]
    (->MapResource m (to-date (now)))))
