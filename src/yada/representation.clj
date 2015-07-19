;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require
   [clojure.tools.logging :refer :all]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [hiccup.page :refer (html5)]
   [manifold.stream :refer [->source transform]]
   [ring.swagger.schema :as rs]
   [ring.util.codec :as codec]
   [yada.mime :as mime]
   [yada.negotiation :as negotiation]
   [yada.resource :as res]
   [clojure.walk :refer (keywordize-keys)]
   [json-html.core :as jh])
  (:import [clojure.core.async.impl.channels ManyToManyChannel]
           [java.io File]
           [java.net URL]
           [manifold.stream.async CoreAsyncSource]))

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

(defmethod render-map "application/json"
  [m _]
  (str (json/encode m) \newline))

(defmethod render-map "application/edn"
  [m _]
  (prn-str m))

(defmethod render-seq "application/json"
  [s _]
  (str (json/encode s) \newline))

(defmethod render-seq "application/edn"
  [s _]
  (prn-str s))

(defmethod render-seq "text/event-stream"
  [s _]
  ;; Transduce the body to server-sent-events
  (transform (map (partial format "data: %s\n\n")) (->source s)))

(defmethod render-map "text/html"
  [m representation]
  (-> (html5
         [:head [:style (-> "json.human.css" clojure.java.io/resource slurp)]]
         (jh/edn->html m))
      (str \newline) ; annoying on the command-line otherwise
      (to-body representation) ; for string encoding
      ))
