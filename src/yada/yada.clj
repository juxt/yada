;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
   yada.bidi
   yada.swagger
   yada.resources.atom-resource
   yada.resources.collection-resource
   yada.resources.file-resource
   yada.resources.misc
   yada.resources.string-resource
   yada.resources.url-resource
   yada.resources.sse
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core yada handler resource]
 [yada.swagger swaggered])

;; Convenience functions, allowing us to encapsulate the context
;; structure.
(defn content-type [ctx]
  (get-in ctx [:response :representation :media-type :name]))

(defn charset [ctx]
  (get-in ctx [:response :representation :charset :alias]))

(defn language [ctx]
  (get-in ctx [:response :representation :language]))
