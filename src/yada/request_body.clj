;; Copyright © 2015, JUXT LTD.

(ns yada.request-body
  (:require
   [byte-streams :as b]
   [clojure.tools.logging :refer :all]
   [ring.swagger.schema :as rs]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [yada.media-type :as mt]))

(def application_octet-stream
  (mt/string->media-type "application/octet-stream"))

(defmulti process-request-body
  (fn [ctx body-stream content-type & args]
    (:name content-type)))

;; We return 418 if there's a content-type which we don't
;; recognise. Using the multimethods :default method is a nice trick for
;; this, because it's open for extension.
(defmethod process-request-body :default
  [ctx body-stream media-type & args]
  (d/error-deferred (ex-info "Unsupported Media Type" {:status 418})))

;; A nil (or missing) Content-Type header is treated as
;; application/octet-stream.
(defmethod process-request-body nil
  [ctx body-stream media-type & args]
  (process-request-body ctx body-stream application_octet-stream))

(defmethod process-request-body "application/octet-stream"
  [ctx body-stream media-type & args]
  (d/chain
   (s/reduce (fn [acc buf] (inc acc)) 0 body-stream)
   ;; 1. Get the body buffer receiver from the ctx - a default one will
   ;; be configured for each method, it will be configurable in the
   ;; service options, since it's an infrastructural concern.

   ;; 2. Send each buffer in the reduce to the body buffer receiver

   ;; 3. At the end of the reduce, as the body buffer receiver to
   ;; provide the context's :body.
   (fn [acc]
     (infof ":default acc is %s" acc)))
  ctx)

;; Deprecated?

;; Coerce request body  ------------------------------

;; The reason we use 2 forms for coerce-request-body is so that
;; schema-using forms can call into non-schema-using forms to
;; pre-process the body.

#_(defmulti coerce-request-body (fn [body media-type & args] media-type))

#_(defmethod coerce-request-body "application/json"
  ([body media-type schema]
   (rs/coerce schema (coerce-request-body body media-type) :json))
  ([body media-type]
   (json/decode body keyword)))

#_(defmethod coerce-request-body "application/octet-stream"
  [body media-type schema]
  (cond
    (instance? String schema) (bs/to-string body)
    :otherwise (bs/to-string body)))

#_(defmethod coerce-request-body nil
  [body media-type schema] nil)

#_(defmethod coerce-request-body "application/x-www-form-urlencoded"
  ([body media-type schema]
   (rs/coerce schema (coerce-request-body body media-type) :query))
  ([body media-type]
   (keywordize-keys (codec/form-decode body))))
