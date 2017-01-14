;; Copyright © 2014-2017, JUXT LTD.

(ns ^{:doc "Test utilities"}
 yada.test-util
  (:require
   [byte-streams :as bs]))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"[0-9a-f]+" etag)))

(defn to-string [s]
  (bs/convert s String))
