;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test utilities"}
  yada.test.util
  (:require
   [byte-streams :as bs]))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"-?\d+" etag)))

(defn to-string [s]
  (bs/convert s String))
