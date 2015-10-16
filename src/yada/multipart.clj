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
  [n index bytes]
  (let [offsets (match index bytes)]
    (concat
     [{:type :info :chunk :start :chunk-index n :chunk-size (count bytes)
       :offsets offsets}]
     (when (pos? (or (first offsets) 0))
       [{:type :tail :from 0 :to (first offsets) :bytes bytes :chunk n}])
     (for [[from to] (partition 2 1 offsets) :let [pos (+ from (:length index))]]
       {:type :part :from from :to to :pos pos :first-bytes (take 2 (drop pos bytes)) :bytes bytes :chunk n})
     (if (last offsets)
       [{:type :head :from (last offsets) :to (count bytes) :bytes bytes :chunk n}]
       [{:type :data :from 0 :to (count bytes) :bytes bytes :chunk n}])
     [{:type :info :chunk :end}])))

(def ^:deprecated split-buf split-chunk-by-boundary)

(defn split-multipart-at-boundaries [index source]
  "Given a source is a (possibly) lazy sequence/stream of chunks (byte-arrays, byte-buffers),
  returns a new source of parts (and pieces of parts where
  necessary). Each vector contains one or more maps containing the
  byte-array data and boundary details. Coercion is via byte-streams.

  The index parameter is the Boyer-Moore index, created by bm-index on a boundary.

  Parts of a multipart stream may span buffers. So can boundary markers. Therefore the returned stream cannot always return whole parts, but may have to return pieces which must be reassembled.

  All entries have a :type entry of the following :-

  :info - start/end chunk markers
  :part - a whole part of a multipart
  :head - the head peice of a part, started with a detected boundary but with no end
  :tail - the tail piece of a part, ended at a detected boundary but with no start
  :data - an inner piece of a part, with no detected start or end boundary
  :end! - no more chunks marker. This is the last piece in the stream
          before it is closed. This indicator is used to flush any state and
          assemble the final part."
  (let [stream (s/stream)]
    (d/loop [n 1]
      (d/chain
       (s/take! source ::drained)
       (fn [buf]
         (if (identical? buf ::drained)
           (do (s/put! stream [{:type :end!}]) ; the 'no-more-chunks' marker
               (s/close! stream))
           (do
             (s/put! stream (split-chunk-by-boundary n index (b/to-byte-array buf)))
             (d/recur (inc n)))))))
    (s/mapcat identity (s/source-only stream))))

(defn- seam
  "Find the position of a potential boundary straddling across a pair of byte-arrays."
  [buf-a buf-b index]
  (let [boundary-size (:length index)]
    (when (and (:bytes buf-a) (:bytes buf-b))
      (let [b (byte-array (* 2 boundary-size))]
        (System/arraycopy (:bytes buf-a) (- (count (:bytes buf-a)) boundary-size) b 0 boundary-size)
        (System/arraycopy (:bytes buf-b) 0 b boundary-size boundary-size)
        (let [pos (first (match index b))]
          ;; Return the result only if the boundary starts in
          ;; buf-a. Otherwise, the boundary starts in buf-b and will be
          ;; known to buf-b and dealt with accordingly.
          (when (< pos boundary-size) pos))))))

(defn match?
  "Is needle in bytes at offset pos?"
  [bytes needle pos]
  (every? true? (map-indexed #(= (get bytes (+ pos %1)) %2) (map byte needle))))

(defn assemble-multipart-pieces
  "Returns a stateful transducer that processes a series of buffer
  events and combines them into a stream of multipart events. Parts will
  be delivered either in entirety, or as a series of
  {partial,continuation*,completion} events. The first part of a partial
  will be at least one buffer size large, so any text headers can be
  processed. Boundaries that straddle buffers are detected and handled."
  [index]
  (let [boundary-size (:length index)]
    (fn [rf]
      (let [mem (volatile! nil)]
        (fn
          ([] (rf))
          ([result] (rf result))
          ([result input]
           (infof "input: %s" (dissoc input :bytes))
           (let [previous @mem
                 seam (seam previous input index)]
             (case (:type input)
               :info (rf result [])
               :part (do
                       (vreset! mem nil)
                       (rf result [input]))

               ;; :tail terminates a part, the question is whether
               ;; there's a boundary straddling the previous input and this
               ;; one
               :tail
               (do
                 (vreset! mem nil)
                 (rf result
                     (if seam
                       (case (:type previous)
                         :head
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

                         :data
                         (throw (ex-info "TODO: head->data" {})))

                       ;; No seam
                       (case (:type previous)
                         ;; leader->remainder (no seam)
                         :head [{:type :part
                                 :from (:from previous)
                                 :to (:to input)
                                 :chunks [(:chunk previous) (:chunk input)]}]
                         ;; block->remainder (no seam)
                         :data [{:type :completion
                                 ;; Whenever block is in mem, only the last boundary size bytes are unserved
                                 :from (- (count (:bytes previous)) boundary-size)
                                 :to (:to input)
                                 :chunks [(:chunk previous) (:chunk input)]}]))))

               :head (do
                       (vreset! mem input)
                       (rf result
                           (if seam
                             (if (:type previous)
                               ;; default
                               [{:type :unknown1 :span [(:type previous) :head] :seam seam}]
                               ;;
                               [{:type :unknown2 :span [(:type previous) :head] :seam seam}])
                             ;; no seam
                             (case (:type previous)
                               ;; TODO: This is only a part if there's a CRLF after the boundary.
                               ;; There's a boundary starting at 0
                               :head [{:type :part
                                       :from 0
                                       :to (count (:bytes previous))
                                       :chunk (:chunk previous)}]
                               nil [] ;; nil is ok, no seam, no previous, we've got the input in mem now
                               []))))

               :data
               (do
                 (vreset! mem input)
                 (rf result
                     (if seam
                       (case (:type previous)
                         :head (throw (ex-info "TODO: head->data (with seam)" {}))
                         :data (throw (ex-info "TODO: data->data (with seam)" {})))
                       (case (:type previous)
                         ;; head->data (without seam)
                         :head [{:type :partial
                                 :from (:from previous)
                                 :pos (+ (:from previous) boundary-size)
                                 :to (- (count (:bytes input)) boundary-size)
                                 :first-bytes (take 2 (drop  (+ (:from previous) boundary-size) (:bytes previous)))
                                 :chunks [(:chunk previous) (:chunk input)]
                                 }]
                         ;; data->data (without seam)
                         :data [{:type :continuation
                                 :from (- (count (:bytes previous)) boundary-size)
                                 :to (- (count (:bytes input)) boundary-size)
                                 :chunks [(:chunk previous) (:chunk input)]}]
                         (throw (ex-info "TODO: possible if malformed body payload (e.g. no initial boundary)" {:type (:type previous)}))))))

               :end!
               (do
                 (rf result
                     (case (:type previous)
                       :head
                       (if (match? (:bytes previous) "--" boundary-size)
                         []
                         [{:type :malformed-multipart
                           :from boundary-size
                           :to (count (:bytes input))
                           :chunk (:chunk input)}])
                       ;; default
                       [{:type :unknown-end!}])))

               ;; otherwise
               (rf result [{:type :otherwise}])))))))))
