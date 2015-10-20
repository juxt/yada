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
   [yada.multipart :refer [split-multipart-at-boundaries assemble-multipart-pieces]]))

#_(defn generate-boundary []
  (str
   "--"
   (apply str (map char (take 30
                              (shuffle
                               (concat
                                (range (int \A) (inc (int \Z)))
                                (range (int \a) (inc (int \z)))
                                (range (int \0) (inc (int \9))))))))))

#_(defn generate-body [n]
  ;; We want to generate an odd number of characters (23), so we don't use Z.
  (apply str (repeat n (apply str (map char (shuffle (range (int \A) (int \Z))))))))

#_(defn line [& args]
  (str (apply str args) "\r\n"))

#_(defn generate-multipart [boundary parts#]
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

#_(defn filter-by-type [t]
  (partial sequence (filter #(= (:type %) t))))

(def CRLF "\r\n")

(defn line-terminate [line]
  (str line CRLF))

(defn make-content [lines width pad]
  (apply str
         (repeat lines
                 (line-terminate (apply str (repeat width (str pad)))))))

(defn part [boundary content]
  (str CRLF "--" boundary "   " CRLF content))

(defn parts [n preamble boundary content]
  (str
   preamble
   (apply str (repeat n (part boundary content)))
   CRLF "--" boundary "--"))

#_(deftest split-multipart-at-boundaries-test []
  (let [boundary "--ABCD"
        source (-> (parts 2 boundary (content 3 8 "0"))
                   (to-chunks 32)
                   s/->source)]
    (given (s/stream->seq (split-multipart-at-boundaries (bm-index (b/to-byte-array boundary)) source))
      count := 10
      [last :type] := :end!
      [(partial map :type) (partial filter #{:head :end!})] := [:head :head :head :end!])))

(defn get-chunks [{:keys [boundary preamble parts# lines# width# chunk-size]}]
  (-> (parts parts# preamble boundary (make-content lines# width# "0"))
      (to-chunks chunk-size)))

(defn get-index [boundary]
  (bm-index (b/to-byte-array (str CRLF "--" boundary))))

(defn get-pieces [{:keys [boundary parts# lines# width# chunk-size] :as spec}]
  (let [index (get-index boundary)
        source (-> spec get-chunks s/->source)]
    (->> source
         (split-multipart-at-boundaries index)
         s/stream->seq)))

(defn get-parts [{:keys [boundary parts# lines# width# chunk-size] :as spec}]
  (let [index (get-index boundary)
        source (-> spec get-chunks s/->source)]
    (->> source
         (split-multipart-at-boundaries index)
         (s/transform (assemble-multipart-pieces index))
         (s/mapcat #(map (fn [x] (-> x
                                    (dissoc :bytes))) %))
         s/stream->seq)))

(defn get-part-size [{:keys [boundary lines# width#]}]
  (+ (count boundary) 2 (* lines# width#)))

;; TODO: Why aren't we seeing {:end? true} in the splits?

(defn filter-relevant [pieces]
  (map #(select-keys % [:type :from :to :chunk :end?])
       (filter #(#{:head :tail :part :data} (:type %))
               pieces)))

;; (b/print-bytes (get-chunks {:boundary "ABCD" :parts# 2 :lines# 3 :width# 10 :chunk-size 16}))
;; (b/print-bytes (get-chunks {:boundary "ABCD" :parts# 2 :lines# 2 :width# 7 :chunk-size 16}))


;; Pieces include the preamble. The last piece is (usually) the
;; multipart termination, and may include a postamble. Pieces include
;; the boundary, including the leading CRLF.

;; Boundary detection includes both the CRLF and '--' before the 'ABCD'
;; boundary.

(get-pieces {:boundary "ABCD" :preamble "preamble" :parts# 2 :lines# 1 :width# 5 :chunk-size 16})

(deftest pieces-test
  (testing "test-1"
    (let [spec {:boundary "ABCD" :preamble "preamble" :parts# 2 :lines# 1 :width# 5 :chunk-size 16}]

      ;; 0: 70 72 65 61 6D 62 6C 65  0D 0A 2D 2D 41 42 43 44      preamble..--ABCD
      ;;    ^t                       ^h
      ;; 1: 20 20 20 0D 0A 30 30 30  30 30 0D 0A 0D 0A 2D 2D         ..00000....--
      ;;    ^d
      ;; 2: 41 42 43 44 20 20 20 0D  0A 30 30 30 30 30 0D 0A      ABCD   ..00000..
      ;;    ^d
      ;; 3: 0D 0A 2D 2D 41 42 43 44  2D 2D                        ..--ABCD--
      ;;    ^h

      (is (= (filter-relevant (get-pieces spec))
             [{:type :tail :from  0 :to  8 :chunk 0}
              {:type :head :from  8 :to 16 :chunk 0}
              {:type :data :from  0 :to 16 :chunk 1}
              {:type :data :from  0 :to 16 :chunk 2}
              {:type :head :from  0 :to 10 :chunk 3}]))

      ;; 0: 70 72 65 61 6D 62 6C 65  0D 0A 2D 2D 41 42 43 44      preamble..--ABCD
      ;;    ^pre                     ^part
      ;; 1: 20 20 20 0D 0A 30 30 30  30 30 0D 0A 0D 0A 2D 2D         ..00000....--
      ;;                                         ^part
      ;; 2: 41 42 43 44 20 20 20 0D  0A 30 30 30 30 30 0D 0A      ABCD   ..00000..
      ;;
      ;; 3: 0D 0A 2D 2D 41 42 43 44  2D 2D                        ..--ABCD--
      ;;    ^part

      (is (= (get-parts spec)
             [{:type :preamble :from [0 0] :to [0 8] :size 8}
              {:type :partial :from [0 8] :to [1 8] :size 16}
              {:type :completion :from [1 8] :to [1 12] :size 4}
              {:type :partial :from [1 12] :to [2 8] :size 12}
              {:type :completion :from [2 8] :to [2 16] :size 8}
              {:type :part :from [3 0] :to [3 10] :size 10}]))))

  (testing "test-2"
    (let [spec {:boundary "ABCD" :parts# 2 :lines# 2 :width# 7 :chunk-size 16}]

      ;; 0: 0D 0A 2D 2D 41 42 43 44  20 20 20 0D 0A 30 30 30      ..--ABCD   ..000
      ;;    ^h
      ;; 1: 30 30 30 30 0D 0A 30 30  30 30 30 30 30 0D 0A 0D      0000..0000000...
      ;;    ^d
      ;; 2: 0A 2D 2D 41 42 43 44 20  20 20 0D 0A 30 30 30 30      .--ABCD   ..0000
      ;;    ^d
      ;; 3: 30 30 30 0D 0A 30 30 30  30 30 30 30 0D 0A 0D 0A      000..0000000....
      ;;    ^d
      ;; 4: 2D 2D 41 42 43 44 2D 2D                                --ABCD--
      ;;    ^d

      (is (= (filter-relevant (get-pieces spec))
             [{:type :head :from 0 :to 16 :chunk 0}
              {:type :data :from 0 :to 16 :chunk 1}
              {:type :data :from 0 :to 16 :chunk 2}
              {:type :data :from 0 :to 16 :chunk 3}
              {:type :data :from 0 :to  8 :chunk 4}]))

      ;; 0: 0D 0A 2D 2D 41 42 43 44  20 20 20 0D 0A 30 30 30      ..--ABCD   ..000
      ;;    ^part
      ;; 1: 30 30 30 30 0D 0A 30 30  30 30 30 30 30 0D 0A 0D      0000..0000000...
      ;;                                                  ^part
      ;; 2: 0A 2D 2D 41 42 43 44 20  20 20 0D 0A 30 30 30 30      .--ABCD   ..0000
      ;;
      ;; 3: 30 30 30 0D 0A 30 30 30  30 30 30 30 0D 0A 0D 0A      000..0000000....
      ;;                                               ^part
      ;; 4: 2D 2D 41 42 43 44 2D 2D                               --ABCD--

      (is (= (get-parts spec)
             [{:type :partial :from [0  0] :to [1 8] :size 24}
              {:type :completion :from [1 8] :to [1 15] :size 7}
              {:type :partial :from [1 15] :to [2 8] :size 9}
              {:type :continuation :from [2 8] :to [3 8] :size 16}
              {:type :completion :from [3 8] :to [3 14] :size 6}
              {:type :epilogue :from [3 14] :to [4 8] :size 10}
              ])))))

(deftest parts-test

  #_(let [parts 2]
      ;; part is 32 bytes. 2 parts is 64 parts, or 3 chunks rem 4.
      (given (get-parts "--ABCD" {:parts# parts :lines# 3
                                  :width# 8 :chunk-size 20})
        count := 2
        (partial map :type) :∀ (partial = :part))))

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
