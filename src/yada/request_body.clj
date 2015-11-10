;; Copyright © 2015, JUXT LTD.

(ns yada.request-body
  (:require
   [byte-streams :as b]
   [clojure.tools.logging :refer :all]
   [ring.swagger.schema :as rs]
   [manifold.stream :as s]))

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
