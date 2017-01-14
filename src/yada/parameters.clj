;; Copyright © 2014-2017, JUXT LTD.

(ns yada.parameters
  (:require
   [clj-time.coerce :as time]
   [clojure.tools.logging :refer :all]
   [manifold.deferred :as d]
   [ring.middleware.params :refer [assoc-query-params]]
   [schema.core :as s]
   [schema.coerce :as sc]
   [schema.utils :refer [error? error-val]]
   [yada.cookies :as cookies]
   [yada.util :as util])
  (:import
   [schema.core RequiredKey OptionalKey]
   [clojure.lang Keyword]))

(def +date-coercions+
  {s/Inst (comp time/to-date time/from-string)})

(defn coercion-matcher [schema]
  (or (sc/string-coercion-matcher schema)
      (+date-coercions+ schema)))

(defn string->uuid [^String x]
  (if (string? x)
    (try
      (java.util.UUID/fromString x)
      (catch Exception _ x))
    x))

(defmacro cond-matcher [& conds]
  (let [x (gensym "x")]
    `(fn [~x]
       (cond
         ~@(for [c conds] `(~c ~x))
         :else ~x))))

(defn string->long [^String x]
  (if (string? x)
    (try
      (Long/valueOf x)
      (catch Exception e
        x))
    x))

(defn string->double [^String x]
  (if (string? x)
    (try
      (Double/valueOf x)
      (catch Exception _ x))
    x))

(defn number->double [^Number x]
  (if (number? x)
    (try
      (double x)
      (catch Exception _ x))
    x))

(defn string->boolean [x]
  (if (string? x)
    (condp = x
      "true" true
      "false" false
      x)
    x))

(def query-coercions
  {s/Keyword sc/string->keyword
   Keyword sc/string->keyword
   s/Uuid string->uuid
   s/Int (cond-matcher
          string? string->long
          number? sc/safe-long-cast)
   Long (cond-matcher
         string? string->long
         number? sc/safe-long-cast)
   Double (cond-matcher
           string? string->double
           number? number->double)
   Boolean string->boolean})

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
  (to-fn [k] sc/string->keyword)
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

(defn path-parameters [ctx]
  (let [request (:request ctx)
        rp (get request :route-params {})
        method (:method ctx)
        schema (or (get-in ctx [:resource :parameters :path])
                   (get-in ctx [:resource :methods method :parameters :path]))]
    (if schema
      (let [coercer (sc/coercer schema query-coercions)]
        (coercer rp))
      rp)))

(defn query-parameters [ctx]
  (let [request (:request ctx)
        qp (:query-params (assoc-query-params request (or (:charset ctx) "UTF-8")))
        method (:method ctx)
        schema (or (get-in ctx [:resource :parameters :query])
                   (get-in ctx [:resource :methods method :parameters :query]))]
    (if schema
      (let [coercer (sc/coercer schema (fn [schema] (+parameter-key-coercions+ schema)))]
        (coercer qp))
      qp)))

(defn header-parameters [ctx]
  (let [request (:request ctx)
        method (:method ctx)
        schema (or (get-in ctx [:resource :parameters :header])
                   (get-in ctx [:resource :methods method :parameters :header]))]
    (when schema
      (let [coercer (sc/coercer (merge schema {s/Str s/Str}) {})]
        (coercer (:headers request))))))

;; TODO: User-defined coercion-matchers (when coercion-matcher
;; (coercion-matcher schema))
;;
;; For now, since this interceptor is only for minimal use-cases,
;; we'll keep things simple and not make the coercion-matcher
;; pluggable.

(defn ^:yada/interceptor parse-parameters
  "Parse request and coerce parameters. Capture cookies."
  [ctx]
  (let [path-parameters (path-parameters ctx)
        query-parameters (query-parameters ctx)
        header-parameters (header-parameters ctx)
        parameters (merge
                    {}
                    (when (not-empty path-parameters) {:path path-parameters})
                    (when (not-empty query-parameters) {:query query-parameters})
                    (when (not-empty header-parameters) {:header header-parameters}))]

    (let [errors (filter (comp error? second) parameters)]
      (if (not-empty errors)
        (d/error-deferred (ex-info "" {:status 400
                                       :errors errors}))
        (assoc ctx :parameters (util/remove-empty-vals parameters))))))
