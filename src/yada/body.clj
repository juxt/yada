;; Copyright © 2015, JUXT LTD.

(ns yada.body
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [clojure.pprint :refer [pprint]]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [hiccup.core :refer [html]]
   [hiccup.page :refer [html5]]
   [json-html.core :as jh]
   [manifold.stream :refer [->source transform]]
   [ring.util.codec :as codec]
   [ring.swagger.schema :as rs]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.resource :as res])
  (:import
   [clojure.core.async.impl.channels ManyToManyChannel]
   [java.io File]
   [java.net URL]
   [manifold.stream.async CoreAsyncSource]
   [manifold.stream SourceProxy]))

;; Coerce request body  ------------------------------

(defmulti coerce-request-body (fn [representation media-type & args] media-type))

(defmethod coerce-request-body "application/json"
  ([representation media-type schema]
   (rs/coerce schema (coerce-request-body representation media-type) :json))
  ([representation media-type]
   (json/decode representation keyword)))

(defmethod coerce-request-body nil
  ([representation media-type schema]
   nil)
  ([representation media-type]
   nil))

(defmethod coerce-request-body "application/x-www-form-urlencoded"
  ([representation media-type schema]
   (rs/coerce schema (coerce-request-body representation media-type) :query))
  ([representation media-type]
   (keywordize-keys (codec/form-decode representation))
   ))

(defprotocol MessageBody
  (to-body [resource representation] "Construct the reponse body for the given resource, given the negotiated representation (metadata)")
  (content-length [_] "Return the size of the resource's representation, if this can possibly be known up-front (return nil if this is unknown)"))

(defmulti render-map (fn [resource representation] (-> representation :content-type mime/media-type)))
(defmulti render-seq (fn [resource representation] (-> representation :content-type mime/media-type)))

(extend-protocol MessageBody

  String
  ;; A String is already its own representation, all we must do now is encode it to a char buffer
  (to-body [s representation]
    (bs/convert s java.nio.ByteBuffer
                {:encoding (or (some-> representation :charset charset/charset)
                               charset/default-platform-charset)}))

  ;; The content-length is NOT the length of the string, but the
  ;; "decimal number of octets, for a potential payload body".
  ;; See  http://mark.koli.ch/remember-kids-an-http-content-length-is-the-number-of-bytes-not-the-number-of-characters
  (content-length [s]
    nil)

  clojure.lang.APersistentMap
  (to-body [m representation]
    (to-body (render-map m representation) representation))

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
