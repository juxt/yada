;; Copyright © 2014-2017, JUXT LTD.

(ns yada.resources.collection-resource
  (:require
   [clj-time.coerce :refer [to-date]]
   [clj-time.core :refer [now]]
   [yada.body :refer [as-body]]
   [yada.charset :as charset]
   [yada.resource :refer [resource ResourceCoercion]])
  (:import [clojure.lang APersistentMap PersistentVector]))

(extend-protocol ResourceCoercion
  APersistentMap
  (as-resource [m]
    (let [last-modified (to-date (now))]
      (resource
       {:properties
        (fn [ctx] {:last-modified last-modified})
        :produces
        [{:media-type #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
          :charset charset/platform-charsets}]
        :methods {:get (as-body m)}
        })))

  PersistentVector
  (as-resource [v]
    (let [last-modified (to-date (now))]
      (resource
       {:properties
        (fn [ctx] {:last-modified last-modified})
        :produces
        [{:media-type #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
          :charset charset/platform-charsets}]
        :methods {:get v}
        }))))
