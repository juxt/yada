;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require
   [clojure.tools.logging :refer :all]
   [clojure.pprint :refer (pprint)]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.walk :refer (keywordize-keys)]
   [hiccup.core :refer [html]]
   [hiccup.page :refer (html5)]
   [json-html.core :as jh]
   [manifold.stream :refer [->source transform]]
   [ring.swagger.schema :as rs]
   [ring.util.codec :as codec]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.negotiation :as negotiation]
   [yada.resource :as res]
   [yada.util :refer (best best-by parse-csv)]
   manifold.stream.async
   clojure.core.async.impl.channels)
  (:import [clojure.core.async.impl.channels ManyToManyChannel]
           [java.io File]
           [java.net URL]
           [manifold.stream.async CoreAsyncSource]))


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

(defn content-type-acceptable?
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

      (and (= (mime/media-type acceptable) "*/*"))
      [(:quality acceptable) 1 (count (:parameters rep)) (:quality rep)])))

(defn highest-content-type-quality
  "Given a collection of acceptable mime-types, return a function that will return the quality."
  [accepts]
  (fn [rep]
    (best (map (partial content-type-acceptable? (:content-type rep)) accepts))))

(defn make-content-type-quality-assessor
  [req k]
  (->
   (->> (get-in req [:headers "accept"]) parse-csv (map mime/string->media-type))
   highest-content-type-quality
   (wrap-quality-assessor :content-type)
   skip-rejected))

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
  (fn [rep]
    (best (map (partial charset-acceptable? (:charset rep)) accepts))))

(defn make-charset-quality-assessor
  [req k]
  (->
   (->> (get-in req [:headers "accept-charset"]) parse-csv (map charset/to-charset-map))
   highest-charset-quality
   (wrap-quality-assessor :charset)
   skip-rejected))

;; From representation

(defmulti from-representation (fn [representation media-type & args] media-type))

(defmethod from-representation "application/json"
  ([representation media-type schema]
   (rs/coerce schema (from-representation representation media-type) :json))
  ([representation media-type]
   (json/decode representation keyword)))

(defmethod from-representation nil
  ([representation media-type schema]
   nil)
  ([representation media-type]
   nil))

(defmethod from-representation "application/x-www-form-urlencoded"
  ([representation media-type schema]
   (rs/coerce schema (from-representation representation media-type) :query))
  ([representation media-type]
   (keywordize-keys (codec/form-decode representation))
   ))

;; Representation means the representation of state, for the purposes of network communication.

(defprotocol Representation
  (to-body [resource representation] "Construct the reponse body for the given resource, given the negotiated representation (metadata)")
  (content-length [_] "Return the size of the resource's representation, if this can possibly be known up-front (return nil if this is unknown)"))

(defmulti render-map (fn [resource representation] (-> representation :content-type mime/media-type)))
(defmulti render-seq (fn [resource representation] (-> representation :content-type mime/media-type)))

(extend-protocol Representation

  String
  ;; A String is already its own representation, all we must do now is encode it to a char buffer
  (to-body [s representation]
    (or
     ;; We need to understand what is the negotiated encoding of the
     ;; string.

     ;; TODO: This actually requires that the implementation has access
     ;; to the negotiated, or server presented, charset. First, the
     ;; parameter needs to be the negotiation result, not just the
     ;; media-type (it might also need transfer encoding
     ;; transformations)

     (bs/convert s java.nio.ByteBuffer
                 {:encoding (or (:server-charset representation)
                                res/default-platform-charset)})))

  ;; The content-length is NOT the length of the string, but the
  ;; "decimal number of octets, for a potential payload body".
  ;; See  http://mark.koli.ch/remember-kids-an-http-content-length-is-the-number-of-bytes-not-the-number-of-characters
  (content-length [s]
    nil)

  clojure.lang.APersistentMap
  (to-body [m representation]
    (to-body (render-map m representation) representation))

  CoreAsyncSource
  (content-length [_] nil)

  File
  (to-body [f _]
    ;; The file can simply go into the body of the Ring response. We
    ;; could transcode it according to the charset if only we knew what
    ;; the file's initial encoding was. (We can't know this.)
    f)
  (content-length [f] (.length f))

  java.nio.ByteBuffer
  (to-body [b _] b)
  (content-length [b] (.remaining b))

  java.io.BufferedInputStream
  (to-body [b _] b)
  (content-length [_] nil)

  java.io.Reader
  (to-body [r _] r)
  (content-length [_] nil)

  Object
  ;; We could implement to-representation here as a pass-through, but it
  ;; is currently useful to have yada fail on types it doesn't know how to
  ;; represent.
  (content-length [_] nil)

  nil
  (to-body [_ _] nil)
  (content-length [_] nil))

;; text/html

(defmethod render-map "text/html"
  [m representation]
  (-> (html5
         [:head [:style (-> "json.human.css" clojure.java.io/resource slurp)]]
         (jh/edn->html m))
      (str \newline) ; annoying on the command-line otherwise
      (to-body representation) ; for string encoding
      ))

;; application/json

(defmethod render-map "application/json"
  [m representation]
  (let [pretty (get-in representation [:content-type :parameters "pretty"])]
    (str (json/encode m {:pretty pretty}) \newline)))

(defmethod render-seq "application/json"
  [s representation]
  (let [pretty (get-in representation [:content-type :parameters "pretty"])]
    (str (json/encode s {:pretty pretty}) \newline)))

;; application/edn

(defmethod render-map "application/edn"
  [m representation]
  (let [pretty (get-in representation [:content-type :parameters "pretty"])]
    (if pretty
      (with-out-str (pprint m))
      (prn-str m))))

(defmethod render-seq "application/edn"
  [s representation]
  (let [pretty (get-in representation [:content-type :parameters "pretty"])]
    (if pretty
      (with-out-str (pprint s))
      (prn-str s))))

;; text/event-stream

(defmethod render-seq "text/event-stream"
  [s _]
  ;; Transduce the body to server-sent-events
  (transform (map (partial format "data: %s\n\n")) (->source s)))

;; defaults

(defmethod render-map :default
  [m representation]
  (throw (ex-info "Attempt to call render-map without a media-type"
                  {:representation representation})))

(defmethod render-seq :default
  [m representation]
  (throw (ex-info "Attempt to call render-seq without a media-type"
                  {:representation representation})))
