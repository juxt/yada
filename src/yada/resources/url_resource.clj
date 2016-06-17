;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [yada.charset :as charset]
   [yada.resource :refer [resource ResourceCoercion]]
   [yada.util :refer [as-file]]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource on the classpath.

(extend-type URL
  ResourceCoercion
  (as-resource [url]
    (resource
     {:properties
      (fn [ctx]
        {:last-modified (when-let [f (as-file url)]
                          (when (.exists f)
                            (Date. (.lastModified f))))})

      :methods
      {:get
       {:produces
        [{:media-type #{(or (ext-mime-type (.getPath url))
                            "application/octet-stream")}
          :charset charset/platform-charsets
          }]
        :response
        (fn [ctx]
          (if (= (get-in ctx [:response :representation :media-type :type]) "text")
            (BufferedReader.
             ;; Is the resource coded in a different charset? Can't use
             ;; default coercion, supply a custom resource.
             (InputStreamReader. (.openStream url) "UTF-8"))
            (.openStream url)))}}})))
