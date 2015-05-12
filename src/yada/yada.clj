;; Copyright © 2015, JUXT LTD.

(ns yada.yada
  (:refer-clojure :exclude [partial])
  (:require
   yada.core
   yada.util
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core yada]
 [yada.util format-http-date])
