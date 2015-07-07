;; Copyright © 2015, JUXT LTD.

(ns yada.string-resource
  (:require
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.resource :refer [Resource ResourceConstructor ResourceFetch platform-charsets]]
   [yada.representation :refer [Representation]]))

(defrecord StringResource [s last-modified]
  ResourceFetch
  (fetch [this ctx] this)
  Resource
  (supported-methods [this ctx] #{:get :head})

  ;; Without attempting to actually parse it (which isn't completely
  ;; impossible) we're not able to guess the media-type of this
  ;; string, so we return nil.
  (produces [this] nil)
  (produces [this ctx] nil)
  (produces-charsets [_ ctx] platform-charsets)

  (exists? [this ctx] true)
  (last-modified [this _] last-modified)
  (get-state [this media-type ctx] s))

(extend-protocol ResourceConstructor
  String
  (make-resource [s]
    (->StringResource s (to-date (now)))))
