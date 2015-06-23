;; Copyright © 2015, JUXT LTD.

(ns yada.representation
  (:require
   [byte-streams :as bs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [hiccup.page :refer (html5)]
   [manifold.stream :refer [->source transform]]
   [ring.swagger.schema :as rs]
   [yada.mime :as mime]
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

;; Representation means the representation of state, for the purposes of network communication.

(defprotocol Representation
  (to-representation [state ^mime/MediaTypeMap media-type] "Represent the state. The me")
  (content-length [_] "Return the size of the resource's representation, if this can possibly be known up-front (return nil if this is unknown)"))

(defmulti render-map (fn [resource media-type] media-type))
(defmulti render-seq (fn [resource media-type] media-type))

;; TODO: what does it mean to have a default content type? Perhaps, this
;; should be a list of content types that the representation can adapt
;; to

(extend-protocol Representation

  String
  ;; A String is already its own representation, all we must do now is encode it to a char buffer
  (to-representation [s media-type]
    (or
     (when (= (:type media-type) "text")
       (when-let [encoding (some-> media-type :parameters (get "charset"))]
         (bs/convert s java.nio.ByteBuffer {:encoding encoding})))
     s))
  (content-length [s] (.length s))

  clojure.lang.APersistentMap
  (to-representation [m media-type] (render-map m (mime/media-type media-type)))

  CoreAsyncSource
  (content-length [_] nil)

  File
  (to-representation [f media-type]
    ;; The file can simply go into the body of the Ring response. We
    ;; could transcode it according to the charset if only we knew what
    ;; the file's initial encoding was. (We can't know this.)
    f)
  (content-length [f] (.length f))

  java.nio.ByteBuffer
  (to-representation [b _] b)
  (content-length [_] nil)

  java.io.BufferedInputStream
  (to-representation [b _] b)
  (content-length [_] nil)

  Object
  ;; We could implement to-representation here as a pass-through, but it
  ;; is currently useful to have yada fail on types it doesn't know how to
  ;; represent.
  (content-length [_] nil)

  nil
  (to-representation [_ _] nil)
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
  [m _]
  (str (html5
        [:head [:style (-> "json.human.css" clojure.java.io/resource slurp)]]
        (jh/edn->html m))
       \newline))
