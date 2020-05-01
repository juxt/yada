;; Copyright © 2014-2017, JUXT LTD.

(ns yada.transit
  (:require
   [byte-streams :as bs]
   [cognitect.transit :as transit]
   [yada.body :refer [render-map render-seq]]
   [yada.request-body :refer [parse-stream process-request-body with-400-maybe default-process-request-body]]))

;; Outbound

(defn ^:private transit-encode [v type]
  (let [baos (java.io.ByteArrayOutputStream. 100)]
    (transit/write (transit/writer baos type {:transform transit/write-meta}) v)
    (.toByteArray baos)))

(defn ^:private transit-json-encode [v pretty?]
  (transit-encode v (if pretty? :json-verbose :json)))

(defn ^:private transit-msgpack-encode [v]
  (transit-encode v :msgpack))

(defmethod render-map "application/transit+json"
  [m representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (transit-json-encode m pretty)))

(defmethod render-seq "application/transit+json"
  [s representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (transit-json-encode s pretty)))

(defmethod render-map "application/transit+msgpack"
  [m representation]
  (transit-msgpack-encode m))

(defmethod render-seq "application/transit+msgpack"
  [s representation]
  (transit-msgpack-encode s))

;; Inbound

(defmethod parse-stream "application/transit+json"
  [_ stream]
  (-> (bs/to-input-stream stream)
      (transit/reader :json)
      (transit/read)
      (with-400-maybe)))

(defmethod process-request-body "application/transit+json"
  [& args]
  (apply default-process-request-body args))

;; application/transit+msgpack

(defmethod parse-stream "application/transit+msgpack"
  [_ stream]
  (-> (bs/to-input-stream stream)
      (transit/reader :msgpack)
      (transit/read)
      (with-400-maybe)))

(defmethod process-request-body "application/transit+msgpack"
  [& args]
  (apply default-process-request-body args))
