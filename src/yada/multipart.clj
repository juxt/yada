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

(defn copy-bytes [source from to]
  (let [len (- to from)
        b (byte-array len)]
    (System/arraycopy source from b 0 len)
    b))

(defn count-transport-padding [from b]
  (let [to (count b)]
    (loop [n from]
      (when (< n to)
        (let [x (get b n)]
          (if-not (#{(byte \tab) (byte \space)} x) ; TODO: This is LWSP-char - find out what the definition of that is
            (if (and (= (get b n) (byte \return))
                     (= (get b (inc n)) (byte \newline)))
              (+ 2 n)
              (throw (ex-info "Malformed boundary" {})) ; try to trigger this via a test
              )
            (recur (inc n))))))))

(defn copy-bytes-after-boundary [boundary-size source from to]
  (copy-bytes source (count-transport-padding (+ from boundary-size) source) to))

;; TODO: Fix match so that it can stop at a limit, rather than having to
;; copy a buffer like this
(defn copy-and-match [{:keys [index window pos]}]
  (let [b (byte-array pos)]
    (System/arraycopy window 0 b 0 pos)
    (match index b)))

(defn advance-window [m amount]
  (assert (pos? amount) "amount must be positive")
  (System/arraycopy (:window m) amount (:window m) 0 (- (:pos m) amount))
  (update-in m [:pos] - amount))

(defmulti process-boundaries (fn [m] (:state m)))

(defmethod process-boundaries :default [m]
  (throw (ex-info "Calling process-boundaries default state" {})))

;; Surely there's a clojure core library fn for this‽ (TODO: check when online)
(defn array-equal? [& args]
  (every? identity (apply map = args)))

(defmethod process-boundaries :start [m]
  (let [preamble-size (first (:positions m))]
    (infof "preamble-size: %s" preamble-size)
    (cond
      (nil? preamble-size)
      (let [starts-with-boundary? (array-equal? (:dash-boundary m) (copy-bytes (:window m) 0 (count (:dash-boundary m))))
            amt (- (:pos m) (:boundary-size m))]
        (s/put! (:stream m) [{:info (if starts-with-boundary? :partial :partial-preamble)
                              :bytes (copy-bytes (:window m) 0 amt)
                              :label 1}])
        (-> m
            (advance-window amt)
            (assoc :state (if starts-with-boundary? :in-partial :in-preamble))))

      (zero? preamble-size) (-> m (assoc :state :part))

      (pos? preamble-size)
      (let [starts-with-boundary? (array-equal? (:dash-boundary m) (copy-bytes (:window m) 0 (count (:dash-boundary m))))]
        (s/put! (:stream m)
                (if starts-with-boundary?
                  (do
                    (infof "preamble-size: %s" preamble-size)
                    [{:type :part
                      :bytes (copy-bytes-after-boundary (count (:dash-boundary m)) (:window m) 0 preamble-size)
                      :label 2}])
                  [{:type :preamble
                    :bytes (copy-bytes (:window m) 0 preamble-size)
                    :label 3}]))
        (-> m
            (advance-window preamble-size)
            (assoc :state :part)
            )))))

;; TODO: Change process-boundaries so that positions is NOT part of m,
;; because it's then returned inside m (and this is a kind of unwanted
;; 'side-effect' - the positions data is stale when m is returned. So
;; use an extra parameter, rather than m itself.).

(defmethod process-boundaries :in-preamble [m]
  (case (count (:positions m))
    0 (let [amt (- (:pos m) (:boundary-size m))]
        (s/put! (:stream m) [{:type :preamble-continuation
                              :bytes (copy-bytes (:window m) 0 amt)
                              :label 4}])
        (advance-window m amt))

    1 (let [e (first (:positions m))]
        (s/put! (:stream m) [{:type :preamble-completion
                              :bytes (copy-bytes (:window m) 0 e)
                              :label 5}])
        (-> m
            (assoc :state :part)
            (advance-window e)))

    (throw (ex-info "TODO: 2+ positions" {:positions# 2}))))

(defmethod process-boundaries :part [m]
  ;; Since the state is :part, we know we at least one position (at 0)
  (let [partitions (partition 2 1 (:positions m))]
    ;; We must have at least 2 positions, which means at least one partition
    (if (not-empty partitions)
      (do
        ;; Ship parts
        (doseq [[s e] partitions]
          (s/put! (:stream m) [{:type :part
                                :bytes (copy-bytes-after-boundary (:boundary-size m) (:window m) s e)
                                :label 6}]))
        ;; Advance
        (advance-window m (last (:positions m))))

      ;; We don't have any partitions, but since the boundary is at 0,
      ;; it's a partial.
      (let [amt (- (:pos m) (:boundary-size m))]
        (s/put! (:stream m) [{:type :partial :bytes (copy-bytes-after-boundary (:boundary-size m) (:window m) 0 amt) :label 7}])
        (-> m
            (advance-window amt)
            (assoc :state :in-partial))))))

(defmethod process-boundaries :in-partial [m]
  (if-let [e (first (:positions m))]
    (do
      ;; Any position found here will be complete the partial and take us back to normal processing.
      (s/put! (:stream m) [{:type :partial-completion :bytes (copy-bytes (:window m) 0 e) :label 8}])

      ;; Ship any other parts we know about
      (doseq [[s e] (partition 2 1 (:positions m))]
        (infof "s and e are %s and %s" s e)
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes (:window m) s e)
                              :label 9}]))

      ;; Advance to last position
      (let [amt (last (:positions m))]
        (cond-> m
          (pos? amt) (advance-window amt)
          true (assoc :state :part))))

    ;; Otherwise there is just a continuation, ship everything but keep some back for the possible boundary
    (let [amt (- (:pos m) (:boundary-size m))]
      (s/put! (:stream m) [{:type :partial-continuation :bytes (copy-bytes (:window m) 0 amt)
                            :label 10}])
      (advance-window m amt))))

(defn make-space [m]
  (try
    (process-boundaries (assoc m :positions (copy-and-match m)))
    (catch Exception e
      (errorf e "Error making space")
      )))

(defn make-space-until-sufficient [m]
  (let [{:keys [pos window chunk] :as m} m]
    (if (> pos (- (count window) (count chunk)))
      ;; This is the continuation function we return to the trampoline
      #(-> m make-space make-space-until-sufficient)
      m)))

(defn trampoline-make-space [m]
  (trampoline make-space-until-sufficient m))

(defn append-chunk
  "Copy over the chunk's bytes into the window"
  [{:keys [window pos] :as m} chunk]
  (System/arraycopy chunk 0 window pos (count chunk))
  (update-in m [:pos] + (count chunk)))

(defn finish-up [m]
  (case (:state m)
    :start
    (let [positions (copy-and-match m)]
      ;; Ship parts
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes (:window m) s e)
                              :label 11}]))

      (if-let [e (+ (last positions) (:boundary-size m))]
        (if (and (>= (:pos m) (+ 2 e))
                 (= (get (:window m) e) (byte \-))
                 (= (get (:window m) (inc e)) (byte \-)))
          (s/put! (:stream m) [{:type :end
                                :epilogue (String. (copy-bytes (:window m) (+ 2 e) (:pos m)))
                                :label 123}])
          (throw (ex-info "No end delimiter" {})) ; try to trigger this via a test
          )
        (throw (ex-info "No end delimiter" {}))))

    :in-preamble
    (let [positions (copy-and-match m)]
      (let [s (first positions)]
        (s/put! (:stream m) [{:type :preamble-completion
                              :bytes (copy-bytes (:window m) 0 s)
                              :label 12}])
        (doseq [[s e] (partition 2 1 positions)]
          (s/put! (:stream m) [{:type :part
                                :bytes (copy-bytes-after-boundary (:boundary-size m) (:window m) s e)
                                :label 12}]))))

    :part
    (let [positions (copy-and-match m)]
      ;; Ship parts
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes-after-boundary (:boundary-size m) (:window m) s e)
                              :label 13}]))

      (if-let [e (+ (last positions) (:boundary-size m))]
        (if (and (>= (:pos m) (+ 2 e))
                 (= (get (:window m) e) (byte \-))
                 (= (get (:window m) (inc e)) (byte \-)))
          (s/put! (:stream m) [{:type :end
                                :epilogue (String. (copy-bytes (:window m) (+ 2 e) (:pos m)))
                                :label 123}])
          (throw (ex-info "No end delimiter" {})) ; try to trigger this via a test
          )
        (throw (ex-info "No end delimiter" {})))

      )

    :in-partial
    (let [positions (copy-and-match m)]
      (when-let [e (first positions)]
        (s/put! (:stream m) [{:type :partial-completion
                              :bytes (copy-bytes (:window m) 0 e)
                              :label 14}]))
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes-after-boundary (:boundary-size m) (:window m) s e)
                              :label 15
                              }]))
      (if-let [e (+ (last positions) (:boundary-size m))]
        (if (and (>= (:pos m) (+ 2 e))
                 (= (get (:window m) e) (byte \-))
                 (= (get (:window m) (inc e)) (byte \-)))
          (s/put! (:stream m) [{:type :end
                                :epilogue (String. (copy-bytes (:window m) (+ 2 e) (:pos m)))
                                :label 16}])
          (throw (ex-info "No end delimiter" {})) ; try to trigger this via a test
          )
        (throw (ex-info "No end delimiter" {}))))

    (s/put! (:stream m) [{:info "finish up unhandled state" :state m}]))

  (s/close! (:stream m))
  (assoc m :state-done))

;; TODO: I think there's a bug with manifold catching of Throwables (or at least arity errors). Try to cause this again.

(defn process-chunk [{:keys [pos window] :as m} chunk]
  (if (identical? ::drained chunk)
    (finish-up m)

    (let [chunk (b/to-byte-array chunk)]
      (-> m
          (assoc :chunk chunk)
          trampoline-make-space
          (append-chunk chunk)))))

(def CRLF "\r\n")

(defn compute-index [delim]
  (bm-index delim))

(defn parse-multipart [boundary window-size buffer-size source]
  (let [stream (s/stream 32)
        delimiter (b/to-byte-array (str CRLF "--" boundary) {:encoding "US-ASCII"})
        index (compute-index delimiter)
        boundary-size (:length index)]

    ;; With great thanks to Zach's manifold docs, forgive this blatant code theft!
    (d/loop [m {:state :start
                :chunk# 0
                :stream stream
                :window (byte-array window-size)
                :pos 0
                :dash-boundary (b/to-byte-array (str "--" boundary) {:encoding "US-ASCII"})
                :index index
                :boundary-size boundary-size}]
      (->
       (d/chain
        (s/take! source ::drained)

        ;; If we got a chunk, run it through `process-chunk`
        (fn [chunk]
          (process-chunk m chunk))

        ;; Wait for the result from `process-chunk` to be realized
        (fn [m]
          (when-not (= (:state m) :done)
            (d/recur (update-in m [:chunk#] inc)))))

       (d/catch clojure.lang.ExceptionInfo
           (fn [e]
             (errorf e "Failed: %s" (ex-data e))
             (d/chain
              (s/put! stream [{:error :error :message (.getMessage e) :data (ex-data e)}])
              (fn [b]
                (s/close! stream)))))


       (d/catch Throwable
           (fn [e]
             (errorf e "Failed: %s" (ex-data e))
             (d/chain
              (s/put! stream [{:error :error :message (.getMessage e)}])
              (fn [b]
                (s/close! stream)))))

       (d/catch Object
           (fn [o]
             (errorf "Failed")
             (d/chain
              (s/put! stream [{:error :error :object o}])
              (fn [b]
                (s/close! stream)))))))

    (->> stream s/source-only (s/mapcat identity))))
