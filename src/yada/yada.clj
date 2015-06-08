;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core yada]
 [yada.representation string->media-type])
