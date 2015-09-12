;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer (Get)]
   [yada.util :refer [as-file]]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource.

(extend-type URL
  p/ResourceCoercion
  (as-resource [url] url)

  p/Properties
  (properties
    ([u]
     {:representations
      [{:media-type #{(ext-mime-type (.getPath u))}
        :charset charset/platform-charsets}]})
    ([u ctx]
     {:last-modified (when-let [f (as-file u)]
                       (when (.exists f)
                         (.lastModified f)))}))

  ;; TODO: This is wrong. First, an InputStreamReader does not coerce the
  ;; encoding, simply converts a byte stream to a character stream and is
  ;; told the encoding OF the byte stream for it to do so. Second, it is
  ;; reasonable to guess the media-type, but the charset must be provided
  ;; by the options - this means that there must be more sophisticated way
  ;; of (representations) accessing the options itself. We must pass
  ;; options to representations. This requires a change to the protocol.

  Get
  (GET [u ctx]
    (if (= (get-in ctx [:response :representation :media-type :type]) "text")
      (BufferedReader.
       (InputStreamReader. (.openStream u) (or (get-in ctx [:response :server-charset]) "UTF-8")))
      (.openStream u))))
