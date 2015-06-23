;; Copyright © 2015, JUXT LTD.

(ns yada.map-resource
  (:require
   [clojure.tools.logging :refer :all]
   [yada.mime :refer (media-type)]
   [yada.resource :refer (Resource platform-charsets)]
   [cheshire.core :as json]
   [json-html.core :as jh]))

(defrecord MapResource [m last-modified]
  Resource
  (produces [_ ctx] #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"})
  (produces-charsets [_ ctx] platform-charsets)
  (exists? [_ ctx] true)
  (last-modified [_ ctx] last-modified)
  (get-state [_ content-type ctx]
    (let [mt (media-type content-type)]
      (case mt
        "application/json" (str (json/encode m) \newline)
        "application/edn" (prn-str m)
        "text/html" (str (jh/edn->html m) \newline)
        ;; This is our fault, we must have declared that we produce the
        ;; media-type, but now we ducking out of creating a
        ;; representation in it.
        (throw (ex-info "Negotiated media type cannot be represented" {:media-type mt}))))))

(defn new-map-resource [m]
  (->MapResource m (java.util.Date.)))
