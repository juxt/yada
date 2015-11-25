;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.util :refer [as-file]]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource on the classpath.

(extend-type URL
  p/ResourceCoercion
  (as-resource [url]
    {:properties
     (fn [ctx]
       {:last-modified (when-let [f (as-file url)]
                         (when (.exists f)
                           (Date. (.lastModified f))))})

     :methods
     {:get
      {:produces
       [{:media-type #{(ext-mime-type (.getPath url))}
         :charset charset/platform-charsets}]
       :handler
       (fn [ctx]
         (if (= (get-in ctx [:response :representation :media-type :type]) "text")
           (BufferedReader.
            ;; Is the resource coded in a different charset? Can't use
            ;; default coercion, supply a custom resource.
            (InputStreamReader. (.openStream url) "UTF-8"))
           (.openStream url)))}}}))
