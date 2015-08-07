;; Copyright © 2015, JUXT LTD.

(ns yada.resources.collection-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.mime :refer (media-type)]
   [yada.resource :refer (ResourceModification ResourceRepresentations ResourceCoercion platform-charsets)]
   [yada.methods :refer [Get GET]]
   [cheshire.core :as json]
   [json-html.core :as jh])
  (:import [clojure.lang APersistentMap]))

(defrecord MapResource [m last-modified]
  ResourceModification
  (last-modified [_ ctx] last-modified)

  Get
  (GET [_ ctx] m)

  ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
      :content-type #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
      :charset platform-charsets}]))

(extend-protocol ResourceCoercion
  APersistentMap
  (make-resource [m]
    (->MapResource m (to-date (now)))))
