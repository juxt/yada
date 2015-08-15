;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
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
 [yada.core resource]
 [yada.swagger swaggered])

;; Convenience functions, allowing us to encapsulate the context
;; structure.
(defn language [ctx]
  (get-in ctx [:response :representation :language]))
