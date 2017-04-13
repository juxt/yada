;; Copyright © 2014-2017, JUXT LTD.

(ns yada.resources.string-resource
  (:require
   [clj-time.coerce :refer [to-date]]
   [clj-time.core :refer [now]]
   [yada.charset :as charset]
   [yada.resource :refer [resource ResourceCoercion]]))

(extend-protocol ResourceCoercion
  String
  (as-resource [s]
    (resource
     {:properties {:last-modified (to-date (now))
                   :version s}
      :methods
      {:get
       {:produces
        [{ ;; Without attempting to actually parse it (which isn't
           ;; completely impossible) we're not able to guess the
           ;; media-type of this string, so we return text/plain.
          :media-type "text/plain"
          :charset charset/platform-charsets}]
        :response (fn [ctx] s)}}})))
