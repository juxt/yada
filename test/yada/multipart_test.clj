;; Copyright © 2015, JUXT LTD.

(ns yada.multipart-test
  (:require
   [byte-streams :as b]
   [clj-index.core :refer [bm-index match]]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [manifold.stream :as s]
   [juxt.iota :refer [given]]
   [yada.media-type :as mt]
   [yada.multipart :refer [->splitter ->multipart-events]]))

(defn generate-boundary []
  (str
   "--"
   (apply str (map char (take 30
                              (shuffle
                               (concat
                                (range (int \A) (inc (int \Z)))
                                (range (int \a) (inc (int \z)))
                                (range (int \0) (inc (int \9))))))))))

(defn generate-body [n]
  ;; We want to generate an odd number of characters (23), so we don't use Z.
  (apply str (repeat n (apply str (map char (shuffle (range (int \A) (int \Z))))))))

(defn line [& args]
  (str (apply str args) "\r\n"))

(defn generate-multipart [boundary parts#]
  (apply str
         (apply str
                (for [n (range parts#)
                      :let [nm (format "content%d" (inc n))]]
                  (str
                   (line boundary)
                   (line (format "Content-Disposition: form-data; name=\"%s\"" nm))
                   (line)
                   (line (generate-body (case n 4 130 16))))))
         (str boundary "--")
         ))

(defn to-chunks [s]
  (b/to-byte-buffers s {:chunk-size 1000}))

(defn filter-by-type [t]
  (partial sequence (filter #(= (:type %) t))))

(deftest multipart-events-test
  (let [boundary (generate-boundary)]
    (let [index (bm-index (b/to-byte-array boundary))
          source (-> (generate-multipart boundary 10)
                     to-chunks
                     s/->source)]
      (let [s (->> source
                   (s/transform (->splitter (count boundary) index))
                   (s/mapcat identity)
                   (s/transform (->multipart-events (count boundary) index))
                   (s/mapcat identity))]
        (given (s/stream->seq s 100)
          [(filter-by-type :part) count] := 9
          [(filter-by-type :partial) count] := 1)))))

(deftest parse-content-type-header
  (let [content-type "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"]
    (given (mt/string->media-type content-type)
      :name := "multipart/form-data"
      :type := "multipart"
      :subtype := "form-data"
      [:parameters "boundary"] := "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")))

(defn as-multipart-byte-buffer
  "Convert a readable to a byte-buffer, with CRLF line endings."
  [r]
  (b/to-byte-buffer
   (apply str (map #(str % "\r\n")
                   (line-seq (io/reader r))))))

(defn create-matcher [boundary]
  (let [index (bm-index (b/to-byte-array boundary))]
    (fn [source]
      (match index source))))

(deftest boundary-search-test
  (is
   (=
    ((create-matcher (str "--" "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"))
     (b/to-byte-array (as-multipart-byte-buffer (io/resource "yada/multipart-1"))))
    '(0 99 199 295))))

(defn break? [bytes pos]
  (= [(get bytes pos)
      (get bytes (+ pos 1))
      (get bytes (+ pos 2))
      (get bytes (+ pos 3))]
     (map byte [13 10 13 10])))

(defn scan-for-break [bytes offset stop]
  (loop [pos offset]
    (cond
      (> pos (- stop 4)) nil
      (break? bytes pos) (+ 4 pos)
      :otherwise (recur (inc pos)))))

#_(let [charset (java.nio.charset.Charset/forName "US-ASCII")
      decoder (.newDecoder charset)

      boundary "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"
      matcher (create-matcher (str "--" boundary))

      multipart (b/to-byte-array (as-multipart-byte-buffer (io/resource "yada/multipart-1")))

      offsets (matcher multipart)
      offset-pairs (partition 2 1 offsets)]

  (for [[start end] offset-pairs
        :let [start (+ start (count boundary) 2)]]
    (when-let [break (scan-for-break multipart start end)]
      {:headers (str/split (.trim (String. multipart start (- break start) charset)) #"\r\n")
       :bytes (let [len (- end break 2)
                b (byte-array len)]
            (System/arraycopy multipart break b 0 len)
            b)}))

  ;; Need to compare last offset to see if this is the last part.  If
  ;; not, we may still have more buffers to follow. Transfer the bytes
  ;; out to disk or some storage.

  ;;(b/print-bytes (.. body slice (position 97) (limit 193)))
  )

(comment
  charbuf (.decode decoder part)
  matcher (re-matcher #"\r\n([^\r]\r\n)\r\n" charbuf)
  groups (when (re-find matcher)
           (re-groups matcher)))

(re-find (re-matcher #"Content-Disposition" "Content-Disposition: form-data; name=\"firstname\""))

;;{:offsets (0 95 191 283)}
;;{:offsets (2 97 193 285)}

(comment
  (.get body b 0 10)
  #_(b/print-bytes (.get body b 0 10))
  (b/print-bytes b))
