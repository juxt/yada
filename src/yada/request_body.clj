;; Copyright © 2014-2017, JUXT LTD.

(ns yada.request-body
  (:require
   [byte-streams :as bs]
   [clojure.edn :as edn]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [ring.util.codec :as codec]
   [ring.util.request :as req]
   [schema.coerce :as sc]
   [schema.utils :refer [error-val error?]]
   [yada.media-type :as mt]
   [yada.parameters :refer [+parameter-key-coercions+]]))

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
        schemas (get-in ctx [:resource :methods (:method ctx) :parameters])]

    (cond
      ;; In Swagger 2.0 you can't have both form and body
      ;; parameters, which seems reasonable
      (or (:form schemas) (:body schemas))
      (let [fields (codec/form-decode
                    body-string
                    (req/character-encoding (:request ctx)))

            coercer (sc/coercer
                     (or (:form schemas) (:body schemas))
                     +parameter-key-coercions+)

            params (coercer fields)]

        (if-not (error? params)
          (assoc-in ctx [:parameters (if (:form schemas) :form :body)] params)
          (d/error-deferred (ex-info "Bad form fields"
                                     {:status 400 :error (error-val params)}))))

      :otherwise (assoc ctx :body body-string))))

;; Looking for multipart/form-data? Its defmethod can be found in
;; yada.multipart.

(defmulti parse-stream (fn [media-type stream] media-type))

(defmulti default-matcher identity)

(defmethod default-matcher :default [_] (constantly nil))

(defn coerced! [raw-data schema schema->matcher]
  (let [coercer (sc/coercer schema schema->matcher)
        result (coercer raw-data)]
    (when (error? result)
      (throw (ex-info "Malformed body" {:status 400 :error (error-val result)})))
    result))

(defn parse-coerce-stream [stream media-type schema matcher]
  (let [parsed (parse-stream media-type stream)]
    (cond->
      {:parsed parsed}
      schema (assoc :coerced (coerced! parsed schema (or matcher (default-matcher media-type)))))))

(defn body-schema [ctx]
  (get-in ctx [:resource :methods (:method ctx) :parameters :body]))

(defn body-matcher [ctx]
  (get-in ctx [:resource :methods (:method ctx) :coercion-matchers :body]))

(defn default-process-request-body
  [ctx body-stream media-type & _]
  (let [result (parse-coerce-stream
                 body-stream
                 media-type
                 (body-schema ctx)
                 (body-matcher ctx))]
    (cond-> (assoc-in ctx [:body] (:parsed result))
            (contains? result :coerced) (assoc-in [:parameters :body] (:coerced result)))))

(defmacro with-400-maybe [& body]
  `(try
     ~@body
     (catch Exception e#
       (throw (ex-info "Malformed body" {:status 400} e#)))))

;; text/plain

(defmethod parse-stream "text/plain"
  [_ stream]
  (-> (bs/to-string stream)
      (with-400-maybe)))

(defmethod default-matcher "text/plain" [_]
  sc/string-coercion-matcher)

(defmethod process-request-body "text/plain"
  [& args]
  (apply default-process-request-body args))

;; application/edn

(defmethod parse-stream "application/edn"
  [_ stream]
  (-> (bs/to-string stream)
      (edn/read-string)
      (with-400-maybe)))

(defmethod process-request-body "application/edn"
  [& args]
  (apply default-process-request-body args))
