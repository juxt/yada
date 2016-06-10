;; Copyright © 2015, JUXT LTD.

(ns yada.resources.collection-resource
  (:require
   [clojure.tools.logging :refer :all]
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.body :refer [as-body]]
   [yada.protocols :as p]
   [yada.resource :refer [resource]])
  (:import [clojure.lang APersistentMap PersistentVector]))

(extend-protocol p/ResourceCoercion
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
