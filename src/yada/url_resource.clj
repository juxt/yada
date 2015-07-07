(ns yada.url-resource
  (:require
   [clojure.java.io :as io]
   [yada.resource :refer (Resource ResourceConstructor)]
   [ring.util.mime-type :refer (ext-mime-type)])
  (:import [java.net URL]
           [java.util Date]))

;; A UrlResource is a Java resource.
(extend-protocol Resource
  URL
  (produces [u ctx] [(ext-mime-type (.getPath u))])
  (produces-charsets [u ctx] nil) ; TODO: Consider defaulting absence of this function to mean nil
  (exists? [_ ctx] true)
  (last-modified [u ctx]
    (let [f (io/file (.getFile u))]
      (when (.exists f)
        (Date. (.lastModified f)))))
  (supported-methods [this _] #{:get :head})
  (get-state [u media-type ctx]
    (.openStream u)
    )
  )


(extend-protocol ResourceConstructor
  URL
  (make-resource [url] url))
