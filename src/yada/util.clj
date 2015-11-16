;; Copyright © 2015, JUXT LTD.

(ns yada.util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [byte-streams :as bs]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   clojure.core.async.impl.protocols)
  (:import
   [clojure.core.async.impl.protocols ReadPort]
   [java.nio ByteBuffer]))

;; Old comment :-
;; If this is something we can take from, in the core.async
;; sense, then call body again. We need this clause here
;; because: (satisfies? d/Deferrable (a/chan)) => true, so
;; (deferrable?  (a/chan) is (consequently) true too.

(defn deferrable?
  "An alternative version of deferrable that discounts
  ReadPort. Otherwise, core.async channels are considered as streams
  rather than values, which isn't what we want."
  [o]
  (and o
       (not (instance? ReadPort o))
       (d/deferrable? o)))


;; ------------------------------------------------------------------------
;; XML Parsing Transducers

(def children (mapcat :content))

(defn tagp [pred]
  (comp children (filter (comp pred :tag))))

(defn tag= [tag]
  (tagp (partial = tag)))

(defn attr-accessor [a]
  (comp a :attrs))

(defn attrp [a pred]
  (filter (comp pred (attr-accessor a))))

(defn attr= [a v]
  (attrp a (partial = v)))

(def text (comp (mapcat :content) (filter string?)))

;; Parsing

(def CRLF "\r\n")
(def OWS #"[ \t]*")
(def http-token #"[!#$%&'*+-\.\^_`|~\p{Alnum}]+")

;; ETags

(defn md5-hash [s]
  (let [digest
        (doto
            (java.security.MessageDigest/getInstance "MD5")
          (.update (.getBytes s) 0 (.length s)))]
    (format "%1$032x" (java.math.BigInteger. 1 (.digest digest)))))

;; CSV

(defn parse-csv [v]
  (when v
    (map str/trim (str/split v #"\s*,\s*"))))


;; Selection

(defn best
  "Pick the best item from a collection. Since we are only interested in
  the best, we can avoid sorting the entire collection, which could be
  inefficient with large collections. The best element is selected by
  comparing items. An optional comparator can be given."
  ([coll]
   (best compare coll))
  ([^java.util.Comparator comp coll]
   (reduce
    (fn [x y]
      (case (. comp (compare x y)) (0 1) x -1 y))
    coll)))

(defn best-by
  "Pick the best item from a collection. Since we are only interested in
  the best, we can avoid sorting the entire collection, which could be
  inefficient with large collections. The best element is selected by
  applying the function given by keyfn to each item and comparing the
  result. An optional comparator can be given. The implementation uses a
  pair to keep hold of the result of applying the keyfn function, to
  avoid the redundancy of calling keyfn unnecessarily."
  ([keyfn coll]
   (best-by keyfn compare coll))
  ([keyfn ^java.util.Comparator comp coll]
   (first ;; of the pair
    (reduce (fn [x y]
              (if-not x
                ;; Our first pair
                [y (keyfn y)]
                ;; Otherwise compare
                (let [py (keyfn y)]
                  (case (. comp (compare (second x) py))
                    (0 1) x
                    -1 [y py]))))
            nil ;; seed
            coll))))


;; URLs

(defn as-file [resource]
  (when resource
    (case (.getProtocol resource)
      "file" (io/file (.getFile resource))
      "jar" (io/file (.getFile (java.net.URL. (first (str/split (.getFile resource) #"!")))))
      nil)))


;; Useful functions

(defn remove-nil-vals [m]
  (reduce-kv (fn [acc k v] (if v (assoc acc k v) acc)) {} m))

;; Stream coercers

(defn to-manifold-stream [in]
  (let [s (s/stream 100)]
    (doseq [b (bs/to-byte-buffers in)]
      (s/put! s b))
    (s/close! s)
    s))

#_(bs/to-byte-buffers
 (java.io.ByteArrayInputStream. (.getBytes "Hello World!")) {:chunk-size 10})
