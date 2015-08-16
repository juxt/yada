;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [clojure.java.io :as io]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer (Get)]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource.

(extend-type URL
  p/ResourceModification
  (last-modified [u ctx]
    (let [f (io/file (.getFile u))]
      (when (.exists f)
        (Date. (.lastModified f)))))

  p/Representations
  (representations [u]
    [{:content-type #{(ext-mime-type (.getPath u))}
      :charset charset/platform-charsets}])

  Get
  (GET [u ctx]
    (if (= (get-in ctx [:response :representation :content-type :type]) "text")
      (BufferedReader.
       (InputStreamReader. (.openStream u) (or (get-in ctx [:response :server-charset]) "UTF-8")))
      (.openStream u)))

  p/ResourceCoercion
  (as-resource [url] url))
