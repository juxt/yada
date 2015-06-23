(ns yada.map-resource
  (:require
   [clojure.tools.logging :refer :all]
   [yada.mime :refer (media-type)]
   [yada.resource :refer (Resource platform-charsets)]
   [cheshire.core :as json]))

(defrecord MapResource [m last-modified]
  Resource
  (produces [_ ctx] #{"application/edn" "application/json;q=0.9"})
  (produces-charsets [_ ctx] platform-charsets)
  (exists? [_ ctx] true)
  (last-modified [_ ctx] last-modified)
  (get-state [_ content-type ctx]
    (case (media-type content-type)
      "application/json" (json/encode m)
      (throw (ex-info "Unsupported media type" {:media-type media-type})))))

(defn new-map-resource [m]
  (->MapResource m (java.util.Date.)))
