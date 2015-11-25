;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test utilities"}
  yada.test.util
  (:require
   [manifold.stream :as s]
   [byte-streams :as bs]))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"-?\d+" etag)))

(defn to-string [s]
  (bs/convert s String))
