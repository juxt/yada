;; Copyright © 2014-2017, JUXT LTD.

;; Requires Swagger

(ns yada.swagger-parameters
  (:require
   [byte-streams :as bs]
   [yada.request-body :refer [process-request-body]]
   [manifold.deferred :as d]
   [ring.middleware.params :refer [assoc-query-params]]
   [schema.core :as s]
   [schema.coerce :as sc]
   [ring.swagger.coerce :as rsc]
   [ring.swagger.schema :as rs]
   [yada.cookies :as cookies]
   [yada.util :as util]
   [ring.util.request :as req]
   [ring.util.codec :as codec]
   [schema.utils :refer [error? error-val]]
   [schema.coerce :refer [coercer
                          string-coercion-matcher
                          string->keyword
                          keyword-enum-matcher
                          set-matcher]]
   [clj-time.coerce :as time])
  (:import [schema.core RequiredKey OptionalKey]
           [clojure.lang Keyword]))

;; Parameter coercions

(def +date-coercions+
  {s/Inst (comp time/to-date time/from-string)})

(defn coercion-matcher [schema]
  (or (string-coercion-matcher schema)
      (+date-coercions+ schema)))

(defprotocol ParameterKey
  "We could walk the parameters to find out whether they are ultimately
  keywords or strings, but this protocol achieves the same thing."
  (to-key [_])
  (to-fn [_]))

(extend-protocol ParameterKey
  OptionalKey
  (to-key [o] (to-key (:k o)))
  (to-fn [o] (to-fn (:k o)))
  RequiredKey
  (to-key [o] (to-key (:k o)))
  (to-fn [o] (to-fn (:k o)))
  Keyword
  (to-key [k]
    (if-let [ns (namespace k)]
      (str ns "/" (name k))
      (name k)))
  (to-fn [k] string->keyword)
  String
  (to-key [s] s)
  (to-fn [s] identity))

(defn- kw-map-matcher [schema]
  (if (instance? clojure.lang.APersistentMap schema)
    (let [fns (into {}
                    (for [k (keys schema)]
                      (if (satisfies? ParameterKey k)
                        [(to-key k) (to-fn k)])))]
      (fn [x]
        (if (map? x)
          (->> x
               (map (fn [[k v]] [((get fns k identity) k) v]))
               (into {}))
          x)))))

(defn- multiple-args-matcher [schema]
  ;; TODO: Need to be a bit careful here, it could be a byte-array [byte].
  (when (instance? clojure.lang.APersistentVector schema)
    (fn [x]
      (if (not (sequential? x)) [x] x))))

(defn +parameter-key-coercions+
  "A matcher that coerces keywords and keyword enums from strings, and
  longs and doubles from numbers on the JVM (without losing precision)"
  [schema]
  (or
   (coercion-matcher schema)
   (kw-map-matcher schema)
   (multiple-args-matcher schema)))


(defmethod process-request-body "application/x-www-form-urlencoded"
  [ctx body-stream media-type & args]
  (let [body-string (bs/to-string body-stream)
        ;; Form and body schemas have to been done at the method level
        ;; - TODO: Build this constraint in yada.schema.
        schemas (get-in ctx [:resource :methods (:method ctx) :parameters])
        matchers (get-in ctx [:resource :methods (:method ctx) :coercion-matchers])
        coercion-matcher (or (:form matchers) (:body matchers))]

    (cond
      ;; In Swagger 2.0 you can't have both form and body
      ;; parameters, which seems reasonable
      (or (:form schemas) (:body schemas))
      (let [fields (codec/form-decode
                    body-string
                    (or (req/character-encoding (:request ctx)) "UTF-8"))

            coercer (sc/coercer
                     (or (:form schemas) (:body schemas))
                     (fn [schema]
                       (or
                        (when coercion-matcher (coercion-matcher schema))
                        (+parameter-key-coercions+ schema)
                        ((rsc/coercer :json) schema))))

            params (coercer fields)]

        (if-not (error? params)
          (assoc-in ctx [:parameters (if (:form schemas) :form :body)] params)
          (d/error-deferred (ex-info "Bad form fields"
                                     {:ctx ctx :status 400 :error (error-val params)}))))

      :otherwise (assoc ctx :body body-string))))

(defn parse-parameters
  "Parse request and coerce parameters. Capture cookies."
  [ctx]
  (assert ctx "parse-parameters, ctx is nil!")

  (let [;; (Cookies are captured in the yada.cookies/consume-cookies inteceptor)
        method (:method ctx)
        request (:request ctx)
        matcher (fn [param-kw]
                  (get-in ctx [:resource :parameters method :coercion-matchers param-kw]))

        schemas (util/merge-parameters (get-in ctx [:resource :parameters])
                                       (get-in ctx [:resource :methods method :parameters]))

        ;; TODO: Creating coercers on every request is unnecessary and
        ;; expensive, should pre-compute them.

        parameters
        {:path (if-let [schema (:path schemas)]
                 (rs/coerce schema
                            (:route-params request)
                            (fn [schema]
                              (or (when-let [cm (matcher :query)]
                                    (cm schema))
                                  ((rsc/coercer :query) schema))))
                 (:route-params request))
         :query (let [qp (:query-params (assoc-query-params request (or (:charset ctx) "UTF-8")))]
                  (if-let [schema (:query schemas)]
                    (let [coercion-matcher (matcher :query)
                          coercer (sc/coercer schema
                                              (fn [schema]
                                                (or (when coercion-matcher
                                                      (coercion-matcher schema))
                                                    (+parameter-key-coercions+ schema)
                                                    ((rsc/coercer :query) schema))))]
                      (coercer qp))
                    qp))

         :header (when-let [schema (:header schemas)]
                   ;; Allow any other headers
                   (let [coercer (sc/coercer (merge schema {s/Str s/Str}) {})]
                     (coercer (:headers request))))}]

    (let [errors (filter (comp error? second) parameters)]
      (if (not-empty errors)
        (d/error-deferred (ex-info "" {:ctx ctx
                                       :status 400
                                       :errors errors}))
        (assoc ctx :parameters (util/remove-empty-vals parameters))))))
