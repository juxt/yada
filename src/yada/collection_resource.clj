;; Copyright © 2015, JUXT LTD.

(ns yada.collection-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.mime :refer (media-type)]
   [yada.resource :refer (Resource ResourceConstructor platform-charsets)]
   [cheshire.core :as json]
   [json-html.core :as jh])
  (:import [clojure.lang APersistentMap]))

(defrecord MapResource [m last-modified]
  Resource
  (produces [_ ctx] #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"})
  (produces-charsets [_ ctx] platform-charsets)
  (exists? [_ ctx] true)
  (last-modified [_ ctx] last-modified)
  (get-state [_ content-type ctx] m))

(extend-protocol ResourceConstructor
  APersistentMap
  (make-resource [m]
    (->MapResource m (to-date (now)))))
