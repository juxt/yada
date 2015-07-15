;; Copyright © 2015, JUXT LTD.

(ns yada.resources.url-resource
  (:require
   [clojure.java.io :as io]
   [yada.resource :refer (Resource ResourceRepresentations ResourceConstructor platform-charsets)]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]
           [java.io BufferedReader InputStreamReader]))

;; A UrlResource is a Java resource.
(extend-protocol Resource
  URL
  (methods [_] #{:get :head})
  (parameters [_] nil)
  #_(produces [u ctx] [(ext-mime-type (.getPath u))])
  #_(produces-charsets [u ctx] nil)
  (exists? [_ ctx] true)
  (last-modified [u ctx]
    (let [f (io/file (.getFile u))]
      (when (.exists f)
        (Date. (.lastModified f)))))
  (request [u method ctx]
    (case method
      :get
      (if (= (get-in ctx [:response :content-type :type]) "text")
        (BufferedReader.
         (InputStreamReader. (.openStream u) (or (get-in ctx [:response :server-charset]) "UTF-8")))
        (.openStream u)))))

(extend-protocol ResourceRepresentations
  URL
  (representations [u]
    [{:content-type #{(ext-mime-type (.getPath u))}
      :charset platform-charsets}]))

(extend-protocol ResourceConstructor
  URL
  (make-resource [url] url))

;; TODO: Try refactoring to use extend-type instead of extend-protocol
