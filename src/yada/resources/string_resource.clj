;; Copyright © 2015, JUXT LTD.

(ns yada.resources.string-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [yada.charset :as charset]
   [yada.protocols :refer [ResourceCoercion]]
   [yada.resource :refer [resource]]
   [yada.util :refer (md5-hash)]))

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

