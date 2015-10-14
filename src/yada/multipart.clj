;; Copyright © 2015, JUXT LTD.

(ns yada.multipart
  (:require
   [clojure.tools.logging :refer :all]
   [clj-index.core :refer [bm-index match]]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [byte-streams :as b]))

;; Ring's multipart-params middleware wraps Apache's commons file-upload
;; library which expects a single input-stream for multipart/* content.

;; yada's approach is different. Firstly, it uses manifold streams which
;; return java.nio.ByteBuffer buffers. This allows asynchronous reads
;; which avoids the thread-per-request limitation of both Ring and
;; commons file-upload.

;; Secondly, we use a faster algorithm (Boyer-Moore, 1977) for finding
;; boundaries in the content in order separate the parts. This algorithm
;; has the useful property of being able to hop through the byte-buffer,
;; rather than comparing each byte in turn.

;; Rather than use a stream-based approach, which slightly diminishes
;; performance, we apply this algorithm to the each byte buffer in
;; turn. If a given buffer does not end in a boundary, we store the
;; partial data until a boundary is found in a subsequent buffer.

(defn split-chunk-by-boundary
  [n index boundary-size bytes]
  (let [offsets (match index bytes)]
    (concat
     [{:chunk :start
       :chunk-index n
       :chunk-size (count bytes)
       :type :info}]
     (when (pos? (or (first offsets) 0))
       [{:type :tail
         :to (first offsets)
         :bytes bytes
         :chunk n}])
     (for [[from to] (partition 2 1 offsets)
           :let [pos (+ from boundary-size)]]
       {:type :part
        :from from :to to :pos pos
        :first-bytes (take 2 (drop pos bytes))
        :bytes bytes
        :chunk n})
     (if (last offsets)
       [{:type :head
         :from (last offsets)
         :bytes bytes
         :chunk n}]
       [{:type :data
         :bytes bytes
         :chunk n}])
     [{:chunk :end
       :type :info}])))

(def ^:deprecated split-buf split-chunk-by-boundary)

(defn split-multipart-at-boundaries [source boundary]
  "Given a source is a (possibly) lazy sequence/stream of chunks (byte-arrays, byte-buffers),
  returns a new source of parts (and pieces of parts where
  necessary). Each vector contains one or more maps containing the
  byte-array data and boundary details. Coercion is via byte-streams.

  Parts of a multipart stream may span buffers. So can boundary markers. Therefore the returned stream cannot always return whole parts, but may have to return pieces whic rec

  All entries have a :type entry of the following :-

  :info - start/end chunk markers
  :part - a whole part of a multipart
  :head - the head peice of a part, started with a detected boundary but with no end
  :tail - the tail piece of a part, ended at a detected boundary but with no start
  :data - an inner piece of a part, with no detected start or end boundary
  :end! - no more chunks marker. This is the last piece in the stream
          before it is closed. This indicator is used to flush any state and
          assemble the final part."
  (let [stream (s/stream)
        index (bm-index (b/to-byte-array boundary))]
    (d/loop [n 1]
      (d/chain
       (s/take! source ::drained)
       (fn [buf]
         (if (identical? buf ::drained)
           (do (s/put! stream [{:type :end!}]) ; the 'no-more-chunks' marker
               (s/close! stream))
           (do
             (s/put! stream (split-buf n index (count boundary) (b/to-byte-array buf)))
             (d/recur (inc n)))))))
    (s/mapcat identity (s/source-only stream))))

(defn ^:deprecated ->splitter
  "Returns a stateful transducer that parses a stream of buffers,
  splitting each element in the stream into parts separated by a
  boundary. The resulting stream of byte arrays can be fed into
  ->multipart-events."
  [boundary-size index]
  (fn [rf]
    (let [seqn (volatile! 0)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result buf]
         (do
           (let [n (vswap! seqn inc)]
             (rf result (split-buf n index boundary-size buf)))))))))

(defn- seam
  "Find the position of a potential boundary straddling across a pair of byte-arrays."
  [buf-a buf-b boundary-size index]
  (assert buf-b)
  (assert (:bytes buf-b) (str "types: " (:type buf-a) "->" (:type buf-b)))
  (when buf-a
    (assert (:bytes buf-a) (str "types: " (:type buf-a) "->" (:type buf-b)))
    (let [b (byte-array (* 2 boundary-size))]
      (System/arraycopy (:bytes buf-a) (- (count (:bytes buf-a)) boundary-size) b 0 boundary-size)
      (System/arraycopy (:bytes buf-b) 0 b boundary-size boundary-size)
      (first (match index b)))))

(defn ->multipart-events
  "Returns a stateful transducer that processes a series of buffer
  events and combines them into a stream of multipart events. Parts will
  be delivered either in entirety, or as a series of
  {partial,continuation*,completion} events. The first part of a partial
  will be at least one buffer size large, so any text headers can be
  processed. Boundaries that straddle buffers are detected and handled."
  [boundary-size index]
  (fn [rf]
    (let [mem (volatile! nil)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result input]
         (infof "input: %s" (dissoc input :bytes))
         (let [previous @mem
               seam (seam previous input boundary-size index)]
           (case (:type input)
             :part (do
                     (vreset! mem nil)
                     (rf result [input]))

             ;; :remainder terminates a part, the question is whether
             ;; there's a boundary straddling the previous input and this
             ;; one
             :remainder
             (do
               (vreset! mem nil)
               (rf result
                   (if seam
                     (case (:type previous)
                       :leader
                       [{:type :part
                         :from (:from previous)
                         :to (+ (count (:bytes previous)) (- boundary-size) seam)
                         :pos (+ (:from previous) boundary-size)
                         :first-bytes (take 2 (drop (+ (:from previous) boundary-size) (:bytes previous)))
                         :chunk (:chunk previous)}
                        {:type :part
                         :from (+ (count (:bytes previous)) (- boundary-size) seam)
                         :to (:to input)
                         :pos seam
                         :first-bytes (take 2 (drop seam (:bytes input)))
                         :chunks [(:chunk previous) (:chunk input)]}]

                       :block
                       (throw (ex-info "TODO: remainder->block" {})))

                     ;; No seam
                     (case (:type previous)
                       ;; leader->remainder (no seam)
                       :leader [{:type :part
                                 :from (:from previous)
                                 :to (:to input)
                                 :chunks [(:chunk previous) (:chunk input)]}]
                       ;; block->remainder (no seam)
                       :block [{:type :completion
                                ;; Whenever block is in mem, only the last boundary size bytes are unserved
                                :from (- (count (:bytes previous)) boundary-size)
                                :to (:to input)
                                :chunks [(:chunk previous) (:chunk input)]}]))))

             :leader (do
                       (vreset! mem input)
                       (rf result
                           (if seam
                             (if (:type previous)
                               ;; default
                               [{:type :unknown :arc [:leader (:type previous)] :seam seam}]
                               ;;
                               [{:type :unknown :arc [:leader (:type previous)] :seam seam}])
                             ;; no seam
                             (if (:type previous)
                               [{:type :unknown :arc [:leader (:type previous)]}]
                               ;; that's ok, no seam, no previous, we've got the input in mem now
                               []))))

             :block
             (do
               (vreset! mem input)
               (rf result
                   (if seam
                     (case (:type previous)
                       :leader (throw (ex-info "TODO: leader->block (with seam)" {}))
                       :block (throw (ex-info "TODO: block->block (with seam)" {}))
                       )
                     (case (:type previous)
                       ;; leader->block (without seam)
                       :leader [{:type :partial
                                 :from (:from previous)
                                 :pos (+ (:from previous) boundary-size)
                                 :to (- (count (:bytes input)) boundary-size)
                                 :first-bytes (take 2 (drop  (+ (:from previous) boundary-size) (:bytes previous)))
                                 :chunks [(:chunk previous) (:chunk input)]
                                 }]
                       ;; block->block (without seam)
                       :block [{:type :continuation
                                :from (- (count (:bytes previous)) boundary-size)
                                :to (- (count (:bytes input)) boundary-size)
                                :chunks [(:chunk previous) (:chunk input)]}]))))

             ;; otherwise
             (rf result []))))))))
