(ns yada.yada
  (:require
   yada.core
   yada.util
   [potemkin :refer (import-vars)]))

(import-vars
 [yada.core yada]
 [yada.util format-http-date])
