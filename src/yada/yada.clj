;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
   yada.resources.atom-resource
   yada.resources.collection-resource
   yada.resources.file-resource
   yada.resources.misc
   yada.resources.string-resource
   yada.resources.url-resource
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core resource])
