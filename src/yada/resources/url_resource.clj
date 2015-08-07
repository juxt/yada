;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [clojure.java.io :as io]
   [yada.resource :refer (ResourceExistence ResourceModification ResourceRepresentations ResourceCoercion platform-charsets)]
   [yada.methods :refer (Get)]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource.

(extend-type URL
  ResourceModification
  (last-modified [u ctx]
    (let [f (io/file (.getFile u))]
      (when (.exists f)
        (Date. (.lastModified f)))))

  ResourceRepresentations
  (representations [u]
    [{:content-type #{(ext-mime-type (.getPath u))}
      :charset platform-charsets}])

  Get
  (GET [u ctx]
    (if (= (get-in ctx [:response :representation :content-type :type]) "text")
      (BufferedReader.
       (InputStreamReader. (.openStream u) (or (get-in ctx [:response :server-charset]) "UTF-8")))
      (.openStream u)))

  ResourceCoercion
  (make-resource [url] url))
