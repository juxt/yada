;; Copyright © 2015, JUXT LTD.

(ns yada.multipart
  (:require
   [clj-index.core :refer [bm-index match]]
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

(defn- seam
  "Find the position of a potential boundary straddling across a pair of byte-arrays."
  [previous input boundary-size index]
  (let [b (byte-array (* 2 boundary-size))]
    (System/arraycopy (:bytes previous) (- (count (:bytes previous)) boundary-size) b 0 boundary-size)
    (System/arraycopy (:bytes input) 0 b boundary-size boundary-size)
    (first (match index b))))

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
         (let [previous @mem]
           (case (:type input)
             :part (rf result [input])

             :leader (do
                       (vreset! mem input)
                       (rf result []))

             ;; :remainder terminates a part, the question is whether
             ;; there's a boundary straddling the previous input and this
             ;; one
             :remainder
             (do
               (vreset! mem nil)

               (rf result
                   (if-let [seam (seam previous input boundary-size index)]
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
                         :chunks [(:chunk previous) (:chunk input)]
                         }]

                       :block
                       (throw (ex-info (format "TODO: %s" (:type previous)) {})))

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

             :block
             (do
               (vreset! mem input)

               (rf result
                   (if-let [seam (seam previous input boundary-size index)]
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

;; [ ] Put algorithm below into a function.
;; [ ] Write tests that cause seams between continuations, testing all cases (without the need for fuzz testing)
;; [ ] Storing already served bytes in :block mem is wasteful, avoid this
;; [ ] Don't use drop to get first two bytes, use get (is there a quick indexed byte-array get operation?)
;; [ ] Parse to CRLFCRLF and strip out 7-bit headers (Content-Disposition)
;; [ ] Integrate with form handler, where each field can be given a particular handler (String capture, etc.)


(defn- split-buf
  [n index boundary-size buf]
  (let [bytes (b/to-byte-array buf)
        offsets (match index bytes)]
    (concat
     [{:chunk-index n
       :chunk-size (count bytes)}]
     (when (pos? (or (first offsets) 0))
       [{:type :remainder
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
       [{:type :leader
         :from (last offsets)
         :bytes bytes
         :chunk n}]
       [{:type :block
         :bytes bytes
         :chunk n}])
     [{:chunk :end}])))

(defn ->splitter
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
