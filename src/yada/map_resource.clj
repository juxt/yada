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
    (infof "m is %s" m)
    (infof "ctx is %s" (with-out-str (clojure.pprint/pprint ctx)))
    (infof "parameters are %s" (:parameters ctx))
    (case (media-type content-type)
      "application/json" (throw (ex-info "TODO" {}))
      (throw (ex-info "Unsuppored media type" {:media-type media-type})))))

(defn new-map-resource [m]
  (->MapResource m (java.util.Date.)))
