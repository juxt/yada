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


#_(s/take! (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))))

#_(s/stream->seq (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))))

#_(s/stream->seq (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))) 100)
