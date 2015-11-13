;; Copyright © 2015, JUXT LTD.

(ns yada.body
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.logging :refer :all]
   [clojure.walk :refer [keywordize-keys]]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [cognitect.transit :as transit]
   [hiccup.core :refer [html h]]
   [hiccup.page :refer [html5]]
   [json-html.core :as jh]
   [manifold.stream :refer [->source transform]]
   [ring.swagger.schema :as rs]
   [ring.util.codec :as codec]
   [ring.util.http-status :refer [status]]
   [schema.core :as s]
   [yada.charset :as charset]
   [yada.journal :as journal]
   [yada.media-type :as mt])
  (:import
   [clojure.core.async.impl.channels ManyToManyChannel]
   [java.io File ByteArrayInputStream ByteArrayOutputStream]
   [java.net URL]
   [manifold.stream.async CoreAsyncSource]
   [manifold.stream SourceProxy]
   (java.nio.charset StandardCharsets)))

;; Coerce request body  ------------------------------

;; The reason we use 2 forms for coerce-request-body is so that
;; schema-using forms can call into non-schema-using forms to
;; pre-process the body.

(defmulti coerce-request-body (fn [body media-type & args] media-type))

(defmethod coerce-request-body "application/json"
  ([body media-type schema]
   (rs/coerce schema (coerce-request-body body media-type) :json))
  ([body media-type]
   (json/decode body keyword)))

(defn ^:private <-transit-string [^String s type]
  (let [i (ByteArrayInputStream. (.getBytes s (. StandardCharsets UTF_8)))
        r (transit/reader i type)]
    (transit/read r)))

(defmethod coerce-request-body "application/transit+json"
  ([representation media-type schema]
    (rs/coerce schema (coerce-request-body representation media-type) :transit+json))
  ([representation _]
    (<-transit-string representation :json)))

(defmethod coerce-request-body "application/octet-stream"
  [body media-type schema]
  (cond
    (instance? String schema) (bs/to-string body)
    :otherwise (bs/to-string body)))

(defmethod coerce-request-body nil
  [body media-type schema] nil)

(defmethod coerce-request-body "application/x-www-form-urlencoded"
  ([body media-type schema]
   (rs/coerce schema (coerce-request-body body media-type) :query))
  ([body media-type]
   (keywordize-keys (codec/form-decode body))))

(defprotocol MessageBody
  (to-body [resource representation] "Construct the reponse body for the given resource, given the negotiated representation (metadata)")
  (content-length [_] "Return the size of the resource's representation, if this can possibly be known up-front (return nil if this is unknown)"))

(defmulti render-map (fn [resource representation] (-> representation :media-type :name)))
(defmulti render-seq (fn [resource representation] (-> representation :media-type :name)))

(defn encode-message [s representation]
  (bs/convert s java.nio.ByteBuffer
              {:encoding (or (some-> representation :charset charset/charset)
                             charset/default-platform-charset)}))

(extend-protocol MessageBody

  String
  ;; A String is already its own representation, all we must do now is encode it to a char buffer
  (to-body [s representation]
    (encode-message s representation))

  ;; The content-length is NOT the length of the string, but the
  ;; "decimal number of octets, for a potential payload body".
  ;; See  http://mark.koli.ch/remember-kids-an-http-content-length-is-the-number-of-bytes-not-the-number-of-characters
  (content-length [s]
    nil)

  clojure.lang.APersistentMap
  (to-body [m representation]
    ;; We always try to arrive at a string which is then converted
    (encode-message (render-map m representation) representation))

  clojure.lang.APersistentVector
  (to-body [v representation]
    (encode-message (render-seq v representation) representation))

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

  SourceProxy
  (to-body [s _] s)
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
       [:head [:style (slurp (io/resource "json.human.css"))]]
         (jh/edn->html m))
      (str \newline) ; annoying on the command-line otherwise
      (to-body representation) ; for string encoding
      ))

(defmethod render-seq "text/html"
  [m representation]
  (-> (html5
         [:head [:style (slurp (io/resource "json.human.css"))]]
         (jh/edn->html m))
      (str \newline) ; annoying on the command-line otherwise
      (to-body representation) ; for string encoding
      ))

;; application/json

(defmethod render-map "application/json"
  [m representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (str (json/encode m {:pretty pretty}) \newline)))

(defmethod render-seq "application/json"
  [s representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (str (json/encode s {:pretty pretty}) \newline)))

;; application/transit+json

(defn ^:private ->transit-string [v type]
  (let [o (ByteArrayOutputStream. 100)
        w (transit/writer o type)]
    (transit/write w v)
    (.toString o)))

(defn ^:private ->transit+json-string [v pretty?]
  (->transit-string v (if pretty? :json-verbose :json)))

(defmethod render-map "application/transit+json"
  [m representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (->transit+json-string m pretty)))

(defmethod render-seq "application/transit+json"
  [s representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (->transit+json-string s pretty)))

;; application/edn

(defmethod render-map "application/edn"
  [m representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (if pretty
      (with-out-str (pprint m))
      (prn-str m))))

(defmethod render-seq "application/edn"
  [s representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
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
  (throw (ex-info (format "No implementation for render-map for media-type: %s" (:name (:media-type representation)))
                  {:representation representation})))

(defmethod render-seq :default
  [m representation]
  (throw (ex-info (format "No implementation for render-seq for media-type: %s" (:name (:media-type representation)))
                  {:representation representation})))


;; Errors

(def ^{:dynamic true
       :doc "When set to logical true, errors will be printed. Defaults to true."}
  *output-errors* true)

(def ^{:dynamic true
       :doc "When set to logical true, errors will be output with their stack-traces. Defaults to true."}
  *output-stack-traces* true)

(defmulti render-error (fn [status error representation ctx]
                         (-> representation :media-type :name)))

(defn get-error-message [code]
  (or (some-> status (get code) :name) "Unknown"))

(defn get-error-description [code]
  (some-> status (get code) :description))

(defmethod render-error "text/html"
  [status error representation {:keys [id options]}]
  (html
   [:body
    [:h1 (format "%d: %s" status (get-error-message status))]
    (when-let [description (get-error-description status)]
      [:p description])

    ;; Only
    (when *output-errors*
      [:div
       [:p (.getMessage error)]
       (when *output-stack-traces*
         [:pre
          (h (with-out-str (pprint error)))])])

    (when-let [path (:journal-browser-path options)]
      [:div
       [:p "Details"]
       [:a {:href (str path (journal/path-for :entry :id id))} id]])

    [:hr]
    [:div
     [:p "yada"]]]))

(defmethod render-error "application/edn"
  [status error representation {:keys [id options]}]
  {:status status
   :message (get-error-message status)
   :id id
   :error error})

(cheshire.generate/add-encoder clojure.lang.ExceptionInfo
                               (fn [ei jg]
                                 (cheshire.generate/encode-map
                                  {:error (str ei)
                                   :data (pr-str (ex-data ei))} jg)))

(defmethod render-error "application/json"
  [status error representation {:keys [id options]}]
  {:status status
   :message (get-error-message status)
   :id id
   :error error})

(defmethod render-error "application/transit+json"
  [status error representation {:keys [id options]}]
  {:status status
   :message (get-error-message status)
   :id id
   :error error})

(defmethod render-error "text/plain"
  [status error representation {:keys [id options]}]
  (str error \newline))

(defmethod render-error :default
  [status error representation {:keys [id options]}]
  nil)


;; Expand on the idea that errors, org/markdown files, etc. can
;; themselves be resources, yielding multiple representations.
