;; Copyright © 2015, JUXT LTD.

(ns yada.multipart
  (:require
   [byte-streams :as b]
   [clojure.string :as str]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [ring.swagger.coerce :as rsc]
   [schema.coerce :as sc]
   [yada.bmh :as bmh]
   [yada.coerce :as coerce]
   [yada.media-type :as mt]
   [yada.request-body :refer [process-request-body]]
   [yada.util :refer [OWS CRLF]])
  (:import
   [java.io ByteArrayInputStream BufferedReader InputStreamReader]
   [java.nio.charset Charset StandardCharsets]))

;; Ring's multipart-params middleware wraps Apache's commons file-upload
;; library which expects a single input-stream for multipart/* content.

;; yada's approach is different. Firstly, it uses manifold streams which
;; return java.nio.ByteBuffer buffers. This allows asynchronous reads
;; which avoids the thread-per-request limitation of both Ring and
;; commons file-upload.

;; Secondly, we use a faster algorithm (Boyer-Moore, 1977) for finding
;; boundaries in the content in order separate the parts. This algorithm
;; has the useful property of being able to hop through the byte-buffer
;; (backwards from the end of the buffer), rather than comparing each
;; byte in turn.

(defn- copy-bytes [source from to]
  (let [len (- to from)]
    (if (neg? len)
      (throw (ex-info (format
                       "Invalid window region (from %s to %s)" from to)
                      {:from from :to to}))
      (let [b (byte-array len)]
        (System/arraycopy source from b 0 len)
        b))))

(defn- count-transport-padding [from to b]
  (loop [n from]
    (when (< n to)
      (let [x (get b n)]
        ;; RFC 822 Section 3.3: LWSP-char = SPACE (32) / HTAB (9)
        (if-not (#{(byte \tab) (byte \space)} x)
          (if (and (= (get b n) (byte \return))
                   (= (get b (inc n)) (byte \newline)))
            (+ (count [\return \newline]) n)
            ;; try to trigger this via a test
            (throw (ex-info "Malformed boundary" {:from from
                                                  :to to
                                                  :n n
                                                  :c1 (char (get b n))
                                                  :c2 (char (get b (inc n)))})))
          (recur (inc n)))))))

(defn- copy-bytes-after-dash-boundary [{:keys [window dash-boundary-size]} from to]
  (copy-bytes window (count-transport-padding (+ from dash-boundary-size) to window) to))

(defn- copy-bytes-after-delimiter [{:keys [window delimiter-size]} from to]
  (copy-bytes window (count-transport-padding (+ from delimiter-size) to window) to))

;; TODO: Fix match so that it can stop at a limit, rather than having to
;; copy a buffer like this
(defn- copy-and-match [{:keys [index window pos]}]
  (bmh/search index window pos))

(defn- advance-window [m amount]
  (assert (pos? amount) "amount must be positive")
  (System/arraycopy (:window m) amount (:window m) 0 (- (:pos m) amount))
  (update-in m [:pos] - amount))

(defmulti ^{:private true} process-boundaries (fn [m] (:state m)))

(defmethod process-boundaries :default [m]
  (throw (ex-info "Calling process-boundaries default state" {})))

(defmethod process-boundaries :start [m]
  (let [first-delimiter (first (:positions m))]

    (if (= 0 first-delimiter)
      (-> m (assoc :state :part))
      (let [at-boundary? (= (seq (:dash-boundary m))
                            (seq (copy-bytes (:window m) 0 (:dash-boundary-size m))))]
        (if (nil? first-delimiter)
          (let [ ;; We need to reduce the amount of bytes to take by
                ;; delimiter-size. We must always have delimiter-size bytes
                ;; in the window, in case some of them represent a new
                ;; undetected delimiter.
                amt (- (:pos m) (:delimiter-size m))]

            (s/put!
             (:stream m)
             [(if at-boundary?
                {:type :partial
                 :bytes (copy-bytes-after-dash-boundary m 0 amt)
                 :debug [:process-boundaries :start :nil-preamble-size at-boundary?]}
                {:type :partial-preamble
                 :bytes (copy-bytes (:window m) 0 amt)
                 :debug [:process-boundaries :start :nil-preamble-size at-boundary?]})])
            (-> m
                (advance-window amt)
                (assoc :state (if at-boundary? :in-partial :in-preamble))))

          ;; else we have a non-zero positive delimiter in the
          ;; window. So 0->delim is the premable, or a whole part
          (do
            (s/put!
             (:stream m)
             (if at-boundary?
               [{:type :part
                 :bytes (copy-bytes-after-dash-boundary m 0 first-delimiter)
                 :debug [:process-boundaries :start :part]}]
               [{:type :preamble
                 :bytes (copy-bytes (:window m) 0 first-delimiter)
                 :debug [:process-boundaries :start :preamble]}]))
            (-> m
                (advance-window first-delimiter)
                (assoc :state :part))))))))

;; TODO: Change process-boundaries so that positions is NOT part of m,
;; because it's then returned inside m (and this is a kind of unwanted
;; 'side-effect' - the positions data is stale when m is returned. So
;; use an extra parameter, rather than m itself.).

(defmethod process-boundaries :in-preamble [m]
  (case (count (:positions m))
    0 (let [amt (- (:pos m) (:delimiter-size m))]
        (s/put! (:stream m) [{:type :preamble-continuation
                              :bytes (copy-bytes (:window m) 0 amt)
                              :debug [:process-boundaries :in-preamble 0]}])
        (advance-window m amt))

    1 (let [e (first (:positions m))]
        (s/put! (:stream m) [{:type :preamble-completion
                              :bytes (copy-bytes (:window m) 0 e)
                              :debug [:process-boundaries :in-preamble 1]}])
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
                                :bytes (copy-bytes-after-delimiter m s e)
                                :debug [:process-boundaries :part :not-empty]}]))
        ;; Advance
        (advance-window m (last (:positions m))))

      ;; We don't have any partitions, but since the boundary is at 0,
      ;; it's a partial.
      (let [amt (- (:pos m) (:delimiter-size m))]
        (s/put! (:stream m) [{:type :partial :bytes (copy-bytes-after-delimiter m 0 amt) :debug [:process-boundaries :part :empty]}])
        (-> m
            (advance-window amt)
            (assoc :state :in-partial))))))

(defmethod process-boundaries :in-partial [m]
  (if-let [e (first (:positions m))]
    (do
      ;; Any position found here will be complete the partial and take us back to normal processing.
      (s/put! (:stream m) [{:type :partial-completion :bytes (copy-bytes (:window m) 0 e) :debug [:process-boundaries :in-partial e]}])

      ;; Ship any other parts we know about
      (doseq [[s e] (partition 2 1 (:positions m))]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes (:window m) s e)
                              :debug [:process-boundaries :in-partial :others]}]))

      ;; Advance to last position
      (let [amt (last (:positions m))]
        (cond-> m
          (pos? amt) (advance-window amt)
          true (assoc :state :part))))

    ;; Otherwise there is just a continuation, ship everything but keep some back for the possible boundary
    (let [amt (- (:pos m) (:dash-boundary-size m))]
      (s/put! (:stream m) [{:type :partial-continuation
                            :bytes (copy-bytes (:window m) 0 amt)
                            :debug [:process-boundaries :in-partial :continuation]}])
      (advance-window m amt))))

(defn- make-space [m]
  (try
    (-> m
        (assoc :positions (copy-and-match m))
        process-boundaries
        (dissoc :positions))
    (catch Exception e
      (errorf e "Error making space %s" e))))

(defn- make-space-until-sufficient [m]
  (let [{:keys [pos window chunk] :as m} m]
    ;;(assert (number? (:pos m)) "in make-space-until-sufficient")
    (if (> pos (- (count window) (count chunk)))
      ;; This is the continuation function we return to the trampoline
      #(-> m make-space make-space-until-sufficient)
      m)))

(defn- trampoline-make-space [m]
  (trampoline make-space-until-sufficient m))

(defn- append-chunk
  "Copy over the chunk's bytes into the window"
  [{:keys [window pos] :as m} chunk]
  (System/arraycopy chunk 0 window pos (count chunk))
  (update-in m [:pos] + (count chunk)))

(defn- finish-up [m]
  (case (:state m)
    :start
    (let [positions (copy-and-match m)]

      (when-let [s (first positions)]
        (let [at-boundary?
              (= (seq (:dash-boundary m))
                 (seq (copy-bytes (:window m) 0 (:dash-boundary-size m))))]
          (s/put! (:stream m) [(if at-boundary?
                                 {:type :part
                                  :bytes (copy-bytes-after-dash-boundary m 0 s)
                                  :debug [:finish-up :start :first-part]}
                                 {:type :preamble
                                  :bytes (copy-bytes (:window m) 0 s)
                                  :debug [:finish-up :start :preamble]})])))

      ;; Ship parts
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes-after-delimiter m s e)
                              :debug [:finish-up :start :parts]}]))

      (if-let [e (last positions)]
        (let [e (+ (last positions) (:delimiter-size m))]
          (if (and (>= (:pos m) (+ 2 e))
                   (= (get (:window m) e) (byte \-))
                   (= (get (:window m) (inc e)) (byte \-)))
            (s/put! (:stream m) [{:type :end
                                  :epilogue (String. (copy-bytes (:window m) (+ (count "--") e) (:pos m)))
                                  :debug [:finish-up :start :epilogue]}])
            (throw (ex-info "Multipart not properly terminated" {:status 400}))))

        ;; No boundary found at all. This can be treated as all preamble and no parts
        (let [at-boundary?
              (= (seq (:dash-boundary m))
                 (seq (copy-bytes (:window m) 0 (:dash-boundary-size m))))]
          
          (s/put! (:stream m) [(if at-boundary?
                                 {:type :part
                                  :bytes (copy-bytes-after-dash-boundary m 0 (:pos m))
                                  :debug [:finish-up :start :part]}
                                 {:type :preamble
                                  :bytes (copy-bytes (:window m) 0 (:pos m))
                                  :debug [:finish-up :start :all-preamble]})
                               {:type :end
                                :epilogue nil
                                :debug [:finish-up :start :all-preamble :end]}]))))

    :in-preamble
    (let [positions (copy-and-match m)]
      (let [s (first positions)]
        (s/put! (:stream m) [{:type :preamble-completion
                              :bytes (copy-bytes (:window m) 0 s)
                              :debug [:finish-up :in-preamble]}])
        (doseq [[s e] (partition 2 1 positions)]
          (s/put! (:stream m) [{:type :part
                                :bytes (copy-bytes-after-delimiter m s e)
                                :debug [:finish-up :in-preamble :parts]}]))))

    :part
    (let [positions (copy-and-match m)]
      ;; Ship parts
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes-after-delimiter m s e)
                              :debug [:finish-up :part :parts]}]))

      (if-let [e (+ (last positions) (:delimiter-size m))]
        (if (and (>= (:pos m) (+ 2 e))
                 (= (get (:window m) e) (byte \-))
                 (= (get (:window m) (inc e)) (byte \-)))
          (s/put! (:stream m) [{:type :end
                                :epilogue (String. (copy-bytes (:window m) (+ (count "--") e) (:pos m)))
                                :debug [:finish-up :part :epilogue]}])
          ;; try to trigger this via a test
          (throw (ex-info "No end delimiter" {})))
        (throw (ex-info "No end delimiter" {}))))

    :in-partial
    (let [positions (copy-and-match m)]
      (when-let [e (first positions)]
        (s/put! (:stream m) [{:type :partial-completion
                              :bytes (copy-bytes (:window m) 0 e)
                              :debug [:finish-up :in-partial e]}]))
      (doseq [[s e] (partition 2 1 positions)]
        (s/put! (:stream m) [{:type :part
                              :bytes (copy-bytes-after-delimiter m s e)
                              :debug [:finish-up :in-partial :parts]
                              }]))
      (if-let [e (+ (last positions) (:delimiter-size m))]
        (if (and (>= (:pos m) (+ 2 e))
                 (= (get (:window m) e) (byte \-))
                 (= (get (:window m) (inc e)) (byte \-)))
          (s/put! (:stream m) [{:type :end
                                :epilogue (String. (copy-bytes (:window m) (+ (count "--") e) (:pos m)))
                                :debug [:finish-up :in-partial :epilogue]}])
          ;; try to trigger this via a test
          (throw (ex-info "No end delimiter" {})))
        (throw (ex-info "No end delimiter" {}))))

    (s/put! (:stream m) [{:info "finish up unhandled state" :state m}]))

  (s/close! (:stream m))
  (assoc m :state :done))

;; TODO: I think there's a bug with manifold catching of Throwables (or
;; at least arity errors). Try to cause this again.

(defn- process-chunk [{:keys [pos window] :as m} chunk]
  (if (identical? ::drained chunk)
    (finish-up m)

    ;; Converting the chunk to a byte-array here also has the effect of
    ;; releasing the netty chunk.  See
    ;; http://ideolalia.com/aleph/literate.html
    (let [chunk (b/to-byte-array chunk)]
      (-> m
          (assoc :chunk chunk)
          trampoline-make-space
          (append-chunk chunk)))))

(defn dash-boundary [boundary]
  (str "--" boundary))

(defn- compute-index [delim]
  (bmh/compute-index (b/to-byte-array delim)))

(defn parse-multipart
  "Asynchronously parse a multipart stream arriving in chunks from the
  given source. Returns a stream emitting pieces which can be assembled
  to form parts of a multipart stream.

  The parts in the multipart should be separated with boundaries, as
  required by RFC 2046. The boundary string is given as an argument.

  Every piece is a map which is labelled with a :type entry. Small
  pieces that form whole parts are labelled with a :type value of :part.

  Larger parts are broken into a sequence of pieces. The first piece is
  labelled :partial, subsequence pieces are
  labelled :partial-continuation except the last one which is
  labelled :partial-completion.

  If there is a preamble in the multipart, this is indicated by a piece
  labelled :preamble. Similar to the handling of large parts, a large
  preamble is broken into pieces
  labelled :partial-preamble, :preamble-continuation
  and :preamble-completion. Consumers should be aware of this in order
  to properly discard a preamble that spans multiple chunks.

  If the multipart content is properly terminated with a correct
  end-marker, the stream is terminated with a piece labelled with :end
  which also contains the epilogue, if any. Typically the epilogue is
  discarded.

  The window-size argument indicates the size of the buffer that will be
  used to analyze incoming chunks. It should ideally be a multiple of
  the maximum expected chunk size, specifically it should be at least
  the size of the maximum expected chunk size (request buffer size) plus
  twice the length of the delimiter (the delimiter is derived from the
  boundary argument).
  "
  [boundary window-size max-chunk-size source]
  (assert boundary)

  (let [stream (s/stream 4096)
        dash-boundary (dash-boundary boundary)
        delimiter (b/to-byte-array (str CRLF dash-boundary) {:encoding "US-ASCII"})
        minimum-window-size (+ (* 2 (count delimiter)) max-chunk-size)]

    (when (< window-size minimum-window-size)
      ;; This window is too small
      (throw (ex-info "Window must be at least 2 times the size of the delimiter, plus the maximum expected chunk size, and should be ideally much larger to avoid performance penalties."
                      {:delimiter-size (count delimiter)
                       :window-size window-size
                       :max-chunk-size max-chunk-size
                       :minimum-window-size minimum-window-size})))

    (d/loop [m {:state :start
                :chunk# 0
                :stream stream
                :window (byte-array window-size)
                :pos 0
                :dash-boundary (b/to-byte-array dash-boundary {:encoding "US-ASCII"})
                :dash-boundary-size (count dash-boundary)
                :delimiter delimiter
                :delimiter-size (count delimiter)
                :index (compute-index delimiter)
                }]
      (->
       (d/chain
        (s/take! source ::drained)

        ;; If we got a chunk, run it through `process-chunk`
        (fn [chunk]
          (try
            (process-chunk m chunk)
            (catch clojure.lang.ExceptionInfo e
              (errorf e "multipart error")
              (d/error-deferred e))
            (catch Exception e
              (errorf e "unexpected multipart error")
              (d/error-deferred e))))

        ;; Wait for the result from `process-chunk` to be realized
        (fn [m]
          (when-not (= (:state m) :done)
            (d/recur (update-in m [:chunk#] inc)))))

       (d/catch clojure.lang.ExceptionInfo
           (fn [e]
             (errorf e "Failed: %s" (ex-data e))
             (d/chain
              (s/put! stream [{:type :error :error e :message (.getMessage e) :data (ex-data e)}])
              (fn [b]
                (s/close! stream)))))

       (d/catch Throwable
           (fn [e]
             (errorf e "Failed: %s" (ex-data e))
             (d/chain
              (s/put! stream [{:type :error :error e :message (.getMessage e)}])
              (fn [b]
                (s/close! stream)))))

       (d/catch Object
           (fn [o]
             (errorf "Failed: %s" o)
             (d/chain
              (s/put! stream [{:type :error :object o}])
              (fn [b]
                (s/close! stream)))))))

    (->> stream s/source-only (s/mapcat identity))))

;; Assembly of multipart/form-data

(defn xf-add-header-info []
  (map
   (fn [piece]
     (or
      (when (#{:part :partial} (:type piece))
        (let [bytes (:bytes piece)]
          (if-let [n (let [limit (- (count bytes) 4)]
                       (loop [n 0]
                         (when (<= n limit)
                           (if (and (= (get bytes n) (byte \return))
                                    (= (get bytes (+ n 1)) (byte \newline))
                                    (= (get bytes (+ n 2)) (byte \return))
                                    (= (get bytes (+ n 3)) (byte \newline)))
                             n
                             (recur (inc n))))))]
            (let [hin (as-> (ByteArrayInputStream. bytes 0 n) %
                        (InputStreamReader. % StandardCharsets/US_ASCII)
                        (BufferedReader. %)
                        (line-seq %)
                        (map #(str/split % #":") %)
                        (map (juxt (comp str/lower-case first)
                                   (comp str/trim second)) %)
                        (into {} %))]

              (merge piece
                     {:headers hin
                      :body-offset (+ 4 n)}))

            ;; No offset? then the whole part
            (merge piece
                   {:headers nil
                    :body-offset 0}))))
      ;; When not :part nor :partial, pass through
      piece))))

(defn parse-content-disposition-header [s]
  (let [[disposition-type params] (rest (re-matches (re-pattern (str "([\\w-]+)(.*)")) s))]
    {:type disposition-type
     :params
     (into {}
           (for [[_ nm v1 v2]
                 (re-seq (re-pattern (str OWS ";" OWS "([\\w-]+)" OWS "=" OWS "(?:([\\w-]+)|\"([\\w-]+)\")")) params)]
             [nm (or v1 v2)]))}))

(defn xf-parse-content-disposition []
  (map
   (fn [piece]
     (if-let [cd (get-in piece [:headers "content-disposition"])]
       (assoc piece :content-disposition (parse-content-disposition-header cd))
       piece))))

;; The point of these protocols is to facilitate advanced users who need
;; to plug-in their own support for large http payloads.

(defprotocol PartConsumer
  (consume-part [_ state part]
    "Return state with part attached")
  (start-partial [_ piece]
    "Return a partial")
  (part-coercion-matcher [_]
    "Return a map between a target type and the function that coerces this type into that type"))

(defprotocol Partial
  (continue [_ piece] "Return thyself")
  (complete [_ state piece] "Return state, with completed partial"))

(defn assemble-into-one-byte-array [coll]
  (b/convert coll (class (byte-array 0))))

;; These default implementations are in-memory. Other implementations
;; could stream to storage.

(defrecord DefaultPart [])

(defrecord DefaultPartial [initial]
  Partial
  (continue [this piece] (update this :pieces (fnil conj []) piece))
  (complete [this state piece]
    (let [part
          (-> initial
              (assoc :type :part)
              (assoc :bytes (assemble-into-one-byte-array
                             (vec
                              (concat [(:bytes initial)]
                                      (map :bytes (:pieces this))
                                      [(:bytes piece)]))))
              (assoc :pieces (map (fn [x] (update x :bytes count)) (concat [initial] (:pieces this) [piece])))
              map->DefaultPart)]
      (update state :parts conj part))))

(defrecord DefaultPartConsumer []
  PartConsumer
  (consume-part [_ state part] (update state :parts (fnil conj []) (map->DefaultPart part)))
  (start-partial [_ piece] (->DefaultPartial piece))
  (part-coercion-matcher [_]
    ;; Coerce a DefaultPart into the following keys
    {String (fn [^DefaultPart part]
              (let [offset (get part :body-offset 0)]
                (String. (:bytes part) offset (- (count (:bytes part)) offset))))}))

(defn reduce-piece
  "Reducing function for assembling pieces into parts. Seed the reduce
  with a map that contains an entry for :consumer with a value
  satisfying PartConsumer."
  [{:keys [state consumer partial] :as acc} piece]
  (case (:type piece)
    :preamble acc
    :preamble-continuation acc
    :preamble-completion acc
    :part (update acc :state (fn [state] (consume-part consumer state piece)))
    :partial (assoc acc :partial (start-partial consumer piece))
    :partial-continuation (update acc :partial (fn [p] (continue p piece)))
    :partial-completion (-> acc
                            (update :state (fn [s] (if-let [p partial]
                                                    (complete p s piece)
                                                    ;; Ignore
                                                    s)))
                            (dissoc :partial))
    :error (throw (if-let [error (:error piece)] error (ex-info "Multipart error" {:error piece})))
    ;; Return the state
    :end (:state acc)))

;; Putting it altogether

(def CHUNK-SIZE 16384)

(defmethod process-request-body "multipart/form-data"
  [ctx body-stream media-type & args]
  (let [content-type (mt/string->media-type (get-in ctx [:request :headers "content-type"]))
        boundary (get-in content-type [:parameters "boundary"])
        request-buffer-size CHUNK-SIZE ; as Aleph default, TODO: derive this
        window-size (* 4 request-buffer-size)
        part-consumer (get-in ctx
                              [:resource :methods (:method ctx) :part-consumer]
                              (->DefaultPartConsumer))]
    (cond
      (not boundary) (throw (ex-info "No boundary parameter in multipart" {:status 400}))
      :otherwise
      (d/chain
       (->> (parse-multipart boundary window-size request-buffer-size body-stream)
            ;; Since we're multipart/form-data, we're expecting each part
            ;; (or part beginning) to have a content-disposition header,
            ;; let's use a transducer to add that info into the part,
            ;; before we hand the part off to the PartConsumer.
            (s/transform (comp (xf-add-header-info)
                               (xf-parse-content-disposition)))
            ;; Now we assemble (via reduction) the parts. We pass each
            ;; part (or partial part) to a consumer that assembles and
            ;; stores the parts. This might be on-heap, off-heap, in a
            ;; database, as a file. etc.
            (s/reduce
             reduce-piece
             {:consumer part-consumer :state {}}))

       (fn [{:keys [parts] :as body}]
         ;; Regardless of parameter schemas, we use the
         ;; Content-Disposition header to produce a map between fields and
         ;; content.  There is no obligation for the part consumer to
         ;; return a byte[]. In fact, it may produce a java.io.File, or
         ;; stream, handle or database key.

         ;; As we're multipart/form-data, let's make use of the expected
         ;; Content-Disposition headers.
         (let [schemas (get-in ctx [:resource :methods (:method ctx) :parameters])
               fields
               (reduce
                (fn [acc part] (cond-> acc
                                 (= (:type part) :part)
                                 (assoc (get-in part [:content-disposition :params "name"]) part)))
                {} parts)]

           (cond
             ;; In Swagger 2.0 you can't have both form and body
             ;; parameters, which seems reasonable
             (or (:form schemas) (:body schemas))
             (let [coercion-matchers (get-in ctx [:resource :methods (:method ctx)
                                                  :coercion-matchers])
                   matcher (or (:form coercion-matchers) (:body coercion-matchers))
                   coercer (sc/coercer
                            (or (:form schemas) (:body schemas))
                            (fn [schema]
                              (or
                               (when matcher (matcher schema))
                               (coerce/+parameter-key-coercions+ schema)
                               ((part-coercion-matcher part-consumer) schema)
                               ((rsc/coercer :json) schema))))
                   params (coercer fields)]
               (if-not (schema.utils/error? params)
                 (assoc-in ctx [:parameters (if (:form schemas) :form :body)] params)
                 (d/error-deferred (ex-info "Bad form fields"
                                            {:status 400 :error (schema.utils/error-val params)}))))

             :otherwise (assoc ctx :body fields))))))))
