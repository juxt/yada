;; Copyright © 2015, JUXT LTD.

(ns yada.request-body
  (:require
   [byte-streams :as bs]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [ring.swagger.coerce :as rsc]
   [ring.swagger.schema :as rs]
   [ring.util.request :as req]
   [ring.util.codec :as codec]
   [schema.coerce :as sc]
   [schema.utils :refer [error?]]
   [yada.coerce :as coerce]
   [yada.media-type :as mt]))

(def application_octet-stream
  (mt/string->media-type "application/octet-stream"))

(defmulti process-request-body
  "Process the request body, filling out and coercing any parameters
  identified in the context."
  (fn [ctx body-stream content-type & args]
    content-type))

;; See rfc7231#section-3.1.1.5 - we should assume application/octet-stream

;; We return 415 if there's a content-type which we don't
;; recognise. Using the multimethods :default method is a way of
;; returning a 415 even if the resource declares that it consumes an
;; (unsupported) media type.
(defmethod process-request-body :default
  [ctx body-stream media-type & args]
  (d/error-deferred (ex-info "Unsupported Media Type" {:status 415})))

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

(defmethod process-request-body "application/x-www-form-urlencoded"
  [ctx body-stream media-type & args]
  
  (let [body-string (bs/to-string body-stream)
        ;; Form and body schemas have to been done at the method level
        ;; - TODO: Build this contraint in yada.schema.
        schemas (get-in ctx [:handler :methods (:method ctx) :parameters])]
    
    (cond
      ;; In Swagger 2.0 you can't have both form and body
      ;; parameters, which seems reasonable
      (or (:form schemas) (:body schemas))
      (let [fields (codec/form-decode
                    body-string
                    (req/character-encoding (:request ctx)))

            coercer (sc/coercer
                     (or (:form schemas) (:body schemas))
                     (fn [schema]
                       (or
                        (coerce/+parameter-key-coercions+ schema)
                        ((rsc/coercer :json) schema))))

            params (coercer fields)]

        (if-not (error? params)
          (assoc-in ctx [:parameters (if (:form schemas) :form :body)] params)
          (d/error-deferred (ex-info "Bad form fields"
                                     {:status 400 :error params}))))

      :otherwise (assoc ctx :body body-string))))

;; Looking for multipart/form-data? Its defmethod can be found in
;; yada.multipart.

(defmethod process-request-body "text/plain"
  [ctx body-stream media-type & args]
  (let [body-string (bs/to-string body-stream)]
    (-> ctx
        ;; TODO: Only if body parameter, and now coerce too!
        (assoc-in [:parameters :body] body-string)
        (assoc-in [:body] body-string))))

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
