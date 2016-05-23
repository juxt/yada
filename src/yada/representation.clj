;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [yada.coerce :refer (to-set to-list)]
   [yada.charset :as charset]
   [yada.media-type :as mt]
   [yada.util :refer (best best-by parse-csv http-token OWS)]
   manifold.stream.async
   clojure.core.async.impl.channels))

;; Proactive negotiation

;; These are higher-order wrappers used by all dimensios of proactive
;; negotiation.

(defn- skip-rejected
  "Short-circuit attempts to process already rejected representation
  metadata."
  [f]
  (fn [rep]
    (if (:rejected rep) rep (f rep))))

(defn- wrap-quality-assessor
  "Return a function that will either reject, or associate a quality, to
  the given representation metadata."
  [f k]
  (fn [rep]
    (if-let [quality (f rep)]
      (assoc-in rep [:qualities k] quality)
      (assoc rep :rejected k))))

;; Content type negotiation

(defn media-type-acceptable?
  "Compare a single acceptable mime-type (extracted from an Accept
  header) and a candidate. If the candidate is acceptable, return a
  sortable vector [acceptable-quality specificity parameter-count
  candidate-quality]. Specificity prefers text/html over text/* over
  */*. Parameter count gives preference to candidates with a greater
  number of parameters, which prefers text/html;level=1 over
  text/html. This meets the criteria in the HTTP
  specifications. Although the preference that should result with
  multiple parameters is not specified formally, candidates that have a
  greater number of parameters are preferred."
  ;; It is possible that these qualities could be coded into a long, since
  ;; "A sender of qvalue MUST NOT generate more than three digits after
  ;; the decimal point.  User configuration of these values ought to be
  ;; limited in the same fashion." -- RFC 7231 Section 5.3.1
  [rep acceptable]
  (when
      ;; TODO: Case sensitivity/insensitivity requirements
      (and (= (:parameters acceptable) (:parameters rep))
           (pos? (:quality acceptable))
           (pos? (:quality rep)))
    (cond
      (and (= (:type acceptable) (:type rep))
           (= (:subtype acceptable) (:subtype rep)))
      [(:quality acceptable) 3 (count (:parameters rep)) (:quality rep)]

      (and (= (:type acceptable) (:type rep))
           (= (:subtype acceptable) "*"))
      [(:quality acceptable) 2 (count (:parameters rep)) (:quality rep)]

      (and (= (:name acceptable) "*/*"))
      [(:quality acceptable) 1 (count (:parameters rep)) (:quality rep)])))

(defn highest-media-type-quality
  "Given a collection of acceptable mime-types, return a function that will return the quality."
  [accepts]
  (fn [rep]
    (best (map (partial media-type-acceptable? (:media-type rep)) accepts))))

(defn make-media-type-quality-assessor
  [req k]
  (let [acceptable-types (->> (or (get-in req [:headers "accept"]) "*/*")
                              parse-csv (map mt/string->media-type))]
    (-> acceptable-types
        highest-media-type-quality
        (wrap-quality-assessor :media-type)
        skip-rejected)))

;; Charsets ------------------------------------

(defn charset-acceptable? [rep acceptable-charset]
  (when
      (and
       (or (= (charset/charset acceptable-charset) "*")
           (and
            (some? (charset/charset acceptable-charset))
            (= (charset/charset acceptable-charset)
               (charset/charset rep)))
           ;; Finally, let's see if their canonical names match
           (and
            (some? (charset/canonical-name acceptable-charset))
            (= (charset/canonical-name acceptable-charset)
               (charset/canonical-name rep))))
       (pos? (:quality acceptable-charset))
       (pos? (:quality rep)))

    [(:quality acceptable-charset) (:quality rep)]))

(defn highest-charset-quality
  "Given a collection of acceptable charsets, return a function that
  will return the quality."
  [accepts]
  (cond
    (nil? accepts)
    ;; No accept-charset header - means the client will accept any charset.
    (fn [rep]
      [(float 1.0) (-> rep :charset :quality)])

    :otherwise
    (fn [rep]
      (best (map (partial charset-acceptable? (:charset rep)) accepts)))))

(defn make-charset-quality-assessor
  [req k]
  (let [acceptable-charsets (some->> (get-in req [:headers "accept-charset"])
                                     parse-csv (map charset/to-charset-map))]
    (-> acceptable-charsets
        highest-charset-quality
        (wrap-quality-assessor :charset)
        skip-rejected)))

;; Encodings ------------------------------------

;; weight = OWS ";" OWS "q=" qvalue

;;   qvalue = ( "0" [ "." 0*3DIGIT ] )
;;            / ( "1" [ "." 0*3("0") ] )

(defn parse-encoding [s]
  (let [[_ coding qvalue]
        (re-matches
         (re-pattern
          (str "(" http-token ")(?:" OWS ";" OWS "q=(0(?:.\\d{0,3})?|1(?:.0{0,3})?))?"))
         s)]
    ;; qvalue could be nil
    (when coding
      {:coding coding
       :quality (if qvalue (Float/parseFloat qvalue) (float 1.0))})))

(defn encoding-acceptable? [rep acceptable-encoding]
  (when (and (#{"*" (:coding rep)} (:coding acceptable-encoding))
             (pos? (:quality acceptable-encoding))
             (pos? (:quality rep)))
    [(:quality acceptable-encoding) (:quality rep)]))

(defn identity-acceptable? [acceptable-encoding]
  (when (and (#{"*" "identity"} (:coding acceptable-encoding)))
    (:quality acceptable-encoding)))

(defn highest-encoding-quality
  "Given a collection of acceptable encodings, return a function that
  will return the quality for a given representation."
  [accepts]
  (cond
    (nil? accepts)
    ;; No accept-encoding header - means the client will accept any encoding
    (fn [rep]
      [(float 1.0) (-> rep :encoding :quality)])

    (empty? accepts)
    (throw (ex-info "TODO" {}))

    :otherwise
    (fn [rep]
      (if-let [encoding (:encoding rep)]
        (best (map (partial encoding-acceptable? encoding) accepts))
        ;; No representation encoding, rule 2 applies.  If the
        ;; representation has no content-coding, then it is acceptable by
        ;; default unless specifically excluded by the Accept-Encoding
        ;; field stating either "identity;q=0" or "*;q=0" without a more
        ;; specific entry for "identity". -- RFC 7231 Section 5.3.4

        (if-let [identity-quality (best (map identity-acceptable? accepts))]
          (if (zero? identity-quality) :rejected [identity-quality (float 1.0)])
          ;; No identity mentioned, it's acceptable
          [(float 1.0) (float 1.0)])))))

(defn make-encoding-quality-assessor
  [req k]
  (let [acceptable-encodings (some->> (get-in req [:headers "accept-encoding"])
                                      parse-csv (map parse-encoding))]
    (-> acceptable-encodings
        highest-encoding-quality
        (wrap-quality-assessor :encoding)
        skip-rejected)))

;; Languages ------------------------------------

(defn parse-language [s]
  (let [[_ lang qvalue]
        (re-matches
         (re-pattern
          (str "(" http-token ")(?:" OWS ";" OWS "q=(0(?:.\\d{0,3})?|1(?:.0{0,3})?))?"))
         s)]
    ;; qvalue could be nil
    (when lang
      {:language (vec (map str/lower-case (str/split lang #"-")))
       :quality (if qvalue (Float/parseFloat qvalue) (float 1.0))})))

(s/defn lang-matches?
  "See RFC 4647 Basic Filtering"
  [rep :- [s/Str] accepts :- [s/Str]]
  (every? some?
          (map #(#{%1 "*"} %2)
               rep
               (concat accepts (repeat nil)))))

(s/defn language-acceptable?
  [rep :- {:language [s/Str]
           :quality java.lang.Float}
   acceptable-language :- {:language [s/Str]
                           :quality java.lang.Float}]
  (when
      (and
       (lang-matches? (:language rep) (:language acceptable-language))
       (pos? (:quality acceptable-language))
       (pos? (:quality rep)))

    [(:quality acceptable-language) (:quality rep)]))

(defn highest-language-quality
  "Given a collection of acceptable languages, return a function that
  will return the quality for a given representation."
  [accepts]
  (if accepts
    (fn [rep]
      (if-let [language (:language rep)]
        (best (map (partial language-acceptable? language) accepts))
        ;; If there is no language in the representation, don't reject,
        ;; just give the lowest score possible.
        [(float 0.001) (float 0.001)]
        ))
    ;; No accept-language here, that's OK, accept anything
    (fn [rep]
      [(float 1.0) (-> rep :language :quality)])))

(defn make-language-quality-assessor
  [req k]
  (let [acceptable-langs (some->> (get-in req [:headers "accept-language"])
                                  parse-csv (map parse-language))]
    (-> acceptable-langs
        highest-language-quality
        (wrap-quality-assessor :language)
        skip-rejected)))

;; Combined proactive negotiation of representations

(defn make-combined-quality-assessor [req]
  (comp
   (make-language-quality-assessor req :language)
   (make-encoding-quality-assessor req :encoding)
   (make-charset-quality-assessor req :charset)
   (make-media-type-quality-assessor req :media-type)))

(def ^{:doc "A selection algorithm that compares each quality in turn,
  only moving to the next quality if the comparison is a draw."}
  agent-preference-sequential-compare
  (juxt
   :media-type
   :charset
   :encoding
   :language))

#_(def ^{:doc "A selection algorithm that multiples qualities together,
  before comparing."}
  agent-preference-compound-quality
  (juxt
   #(* (or (-> % :media-type first) 1)
       (or (-> % :charset first) 1)
       (or (-> % :encoding first) 1)
       (or (-> % :language first) 1))
   #(* (or (-> % :media-type second) 1)
       (or (-> % :charset second) 1)
       (or (-> % :encoding second) 1)
       (or (-> % :language second) 1))))

(defn select-best-representation
  "Given a request and a collection of representations, pick the best
  representation. This scores each representation against each of 4
  dimensions, each score contributes to its overall rating, the best
  being decided by the given algorithm, which defaults to
  'agent-preference-sequential-compare'."
  ([req reps]
   (select-best-representation req reps agent-preference-sequential-compare))
  ([req reps rater]
   (let [best
         (->> reps
              (map (make-combined-quality-assessor req))
              (filter (comp not :rejected))
              (best-by (comp rater :qualities)))]
     (-> best (dissoc :qualities)))))

;; TODO: Might be able to remove this, because yada.resource now does
;; automatic coercions. However, still some requirement for checking bad
;; charsets, which could be moved into yada.resource now.
#_(defn coerce-representations
  "For performance reasons it is sensible to coerce the representations
  ahead of time, rather than on each request."
  [reps]
  (when reps
    (mapv
     (fn [rep]
       (merge
        (select-keys rep [:method])
        (when-let [ct (:media-type rep)]
          {:media-type (set (map mt/string->media-type (to-set ct)))})
        (when-let [cs (:charset rep)]
          {:charset (set (map charset/to-charset-map (to-set cs)))})

        ;; Check to see if the server-specified charset is
        ;; recognized (registered with IANA). If it isn't we
        ;; throw a 500, as this is a server error. (It might be
        ;; necessary to disable this check in future but a
        ;; balance should be struck between giving the
        ;; developer complete control to dictate charsets, and
        ;; error-proofing. It might be possible to disable
        ;; this check for advanced users if a reasonable case
        ;; is made.)
        #_(when-let [bad-charset
                     (some (fn [mt] (when-let [charset (some-> mt :parameters (get "charset"))]
                                     (when-not (charset/valid-charset? charset) charset)))
                           available-media-types)]
            (throw (ex-info (format "Resource or service declares it produces an unknown charset: %s" bad-charset) {:charset bad-charset})))

        (when-let [enc (:encoding rep)]
          {:encoding (set (map parse-encoding (conj (to-set enc) "identity")))})
        (when-let [langs (:language rep)]
          {:language (set (map parse-language (to-set langs)))})))
     reps)))

(defn vary
  "From a representation-seq, find the variable dimensions"
  [reps]
  (cond-> #{}
    (< 1 (count (distinct (keep (comp #(when % (:name %)) :media-type) reps))))
    (conj :media-type)

    (< 1 (count (distinct (keep (comp #(when % (charset/charset %)) :charset) reps))))
    (conj :charset)

    (< 1 (count (distinct (keep (comp :coding :encoding) reps))))
    (conj :encoding)

    (< 1 (count (distinct (keep (comp :language :language) reps))))
    (conj :language)))

(defn to-vary-header
  "From the result of vary, construct a header"
  [vary]
  (str/join ", "
            (keep {:media-type "accept"
                   :charset "accept-charset"
                   :encoding "accept-encoding"
                   :language "accept-language"}
                  vary)))
