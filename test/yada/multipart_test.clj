;; Copyright © 2015, JUXT LTD.

(ns yada.multipart-test
  (:require
   [byte-streams :as b]
   [clj-index.core :refer [bm-index match]]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [juxt.iota :refer [given]]
   [yada.media-type :as mt]
   [yada.multipart :refer [->splitter ->multipart-events split-multipart-at-boundaries
                           split-buf]]))

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
         (str boundary "--")))

(defn to-chunks [s size]
  (b/to-byte-buffers s {:chunk-size size}))

(defn filter-by-type [t]
  (partial sequence (filter #(= (:type %) t))))

#_(deftest multipart-events-test
  (let [boundary (generate-boundary)]
    (let [index (bm-index (b/to-byte-array boundary))
          source (-> (generate-multipart boundary 10)
                     (to-chunks 1000)
                     s/->source)]
      (let [s (->> source
                   (s/transform (->splitter (count boundary) index))
                   (s/mapcat identity)
                   (s/filter :type)
                   (s/transform (->multipart-events (count boundary) index))
                   (s/mapcat identity))]
        (given (s/stream->seq s 100)
          [(filter-by-type :part) count] := 9
          [(filter-by-type :partial) count] := 1)))))

;; boundary is ABC
;; fill with 0
;; make the test data very small

;; 8 chars wide
;; --ABCD
;; 000000
;; 000000
;; --ABCD
;; 000000
;; 000000

;; buffer size = 24

(defn line-terminate [line]
  (str line "\r\n"))

(defn content [lines width pad]
  (apply str
         (repeat lines
                 (line-terminate (apply str (repeat (- width 2) (str pad)))))))

(defn part [boundary content]
  (str (line-terminate boundary) content))

(defn parts [n boundary content]
  (apply str (repeat n (part boundary content))))

(deftest split-multipart-at-boundaries-test []
  (let [boundary "--ABCD"
        source (-> (parts 2 boundary (content 3 8 "0"))
                   (to-chunks 32)
                   s/->source)]
    (given (s/stream->seq (split-multipart-at-boundaries source boundary))
      count := 7
      [last :type] := :end!
      [(partial map :type) (partial filter #{:head :end!})] := [:head :head :end!])))

#_(deftest chunk-64-into-32-test
  (let [boundary "--ABCD"]
    (let [index (bm-index (b/to-byte-array boundary))
          source (-> (parts 2 "--ABCD" (content 3 8 "0"))
                     (to-chunks 32)
                     s/->source)]
      (let [s (->> source
                   (s/transform (->splitter (count boundary) index))
                   (s/mapcat identity)
                   (s/filter :type)
                   (s/transform (->multipart-events (count boundary) index))
                   (s/mapcat identity))]
        (is (some? (s/stream->seq s 100)))))))



#_(deftest chunk-64-into-20-test
  (let [boundary "--ABCD"]
    (let [index (bm-index (b/to-byte-array boundary))
          source (-> (parts 2 "--ABCD" (content 3 8 "0"))
                     (to-chunks 20)
                     s/->source)]
      (let [s (->> source
                   (s/transform (->splitter (count boundary) index))
                   (s/mapcat identity)
                   (s/filter :type)
                   (s/transform (->multipart-events (count boundary) index))
                   (s/mapcat identity))]
        (is (= (map :type (s/stream->seq s 100)) [:part :partial]))))))

;; What about a stream that may be drained?

#_(deftest drained-test
  (let [boundary "--ABCD"]
    (let [index (bm-index (b/to-byte-array boundary))
          source (-> (parts 2 "--ABCD" (content 3 8 "0"))
                     (to-chunks 20)
                     s/->source)
          stream (s/stream 10)]
      (d/loop [n 1]
        (d/chain (s/take! source ::drained)
                 (fn [buf]
                   (if (identical? buf ::drained)
                     (s/put! stream [::drained!])
                     (s/put! stream (split-buf n index (count boundary) buf)))
                   (when-not (identical? buf ::drained)
                     (d/recur (inc n))))))
      (is (= (last (s/stream->seq (s/mapcat identity stream) 100))
             ::drained!)))))


;; [X] Put algorithm below into a function.
;; [X] Make it easier to define size of parts and boundaries
;; [ ] Fix leader->leader case
;; [ ] Write tests that cause seams between continuations, testing all cases (without the need for fuzz testing) - smoke out the TODOs
;; [ ] Compile byte arrays for events (not just metadata)
;; [ ] Storing already served bytes in :block mem is wasteful, avoid this
;; [ ] Don't use drop to get first two bytes, use get (is there a quick indexed byte-array get operation?)
;; [ ] Parse to CRLFCRLF and strip out 7-bit headers (Content-Disposition)
;; [ ] Integrate with form handler, where each field can be given a particular handler (String capture, etc.)

;; --------------------------------------------------------------------------------

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
