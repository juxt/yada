;; Copyright © 2015, JUXT LTD.

(ns yada.resources.string-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.util :refer (md5-hash)]))

(extend-protocol p/ResourceCoercion
  String
  (as-resource [s]
    {;; TODO Would be nice if properties could be static too.
     :properties (fn [ctx] {:last-modified (to-date (now))
                           :version s})

     :produces [{;; Without attempting to actually parse it (which isn't completely
                 ;; impossible) we're not able to guess the media-type of this
                 ;; string, so we return text/plain.
                 :media-type "text/plain"
                 :charset charset/platform-charsets}]
     ;; TODO: We had a lot of problems with parameter association when
     ;; it was get {:get s}, which is the preferred shorthand. We must
     ;; add a method expansion short-hand. All short-hands must be documented
     :methods {:get {:handler (fn [ctx] s)}}
     }))
