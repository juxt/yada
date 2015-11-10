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

(defn to-manifold-stream [in]
  (let [s (s/stream 100)]
    (doseq [b (bs/to-byte-buffers in)]
      (s/put! s b)
      (s/close! s))
    s))


#_(s/take! (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))))

#_(s/stream->seq (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))))

#_(s/stream->seq (to-manifold-stream (java.io.ByteArrayInputStream. (.getBytes "Hello World!"))) 100)
