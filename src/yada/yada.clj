;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
   yada.swagger
   yada.resources.atom-resource
   yada.resources.collection-resource
   yada.resources.file-resource
   yada.resources.string-resource
   yada.resources.url-resource
   yada.resources.sse
   yada.util
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core yada]
 [yada.swagger swaggered]
 [yada.resource resource]
 [yada.protocols as-resource]
 [yada.util get-host-origin])

;; Convenience functions, allowing us to encapsulate the context
;; structure.
(defn content-type [ctx]
  (get-in ctx [:response :produces :media-type :name]))

(defn charset [ctx]
  (get-in ctx [:response :produces :charset :alias]))

(defn language [ctx]
  (get-in ctx [:response :produces :language]))
