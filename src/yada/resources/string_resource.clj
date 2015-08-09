;; Copyright © 2015, JUXT LTD.

(ns yada.resources.string-resource
  (:require
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.resource :refer [ResourceExistence ResourceModification ResourceRepresentations ResourceVersion ResourceCoercion platform-charsets]]
   [yada.methods :refer [Get Options]]
   [yada.representation :refer [Representation]]
   [yada.util :refer (md5-hash)]))

(defrecord StringResource [s last-modified]
  ResourceRepresentations
  (representations [_]
    [{ ;; Without attempting to actually parse it (which isn't completely
      ;; impossible) we're not able to guess the media-type of this
      ;; string, so we return text/plain.
      :content-type "text/plain"
      :charset platform-charsets}])

  ResourceModification
  (last-modified [_ _] last-modified)

  ResourceVersion
  (version [_ _] s)

  Get
  (GET [_ _] s))

(extend-protocol ResourceCoercion
  String
  (make-resource [s]
    (->StringResource s (to-date (now)))))
