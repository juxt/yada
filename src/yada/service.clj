;; Copyright © 2015, JUXT LTD.

;; TODO No longer just service options, so either rename to options (or
;; similar) or fold into core.

(ns yada.service
  (:require [clojure.core.async.impl.protocols :as aip]
            [clojure.set :as set]
            [clojure.tools.logging :refer :all :exclude [trace]]
            [manifold.deferred :as d]
            [manifold.stream :refer (->source transform)]
            [yada.mime :refer (media-type)]
            [yada.util :refer (deferrable?)])
  (import [clojure.core.async.impl.protocols ReadPort]
          [yada.mime MediaTypeMap]
          [java.io File]
          [java.util Date]))

(defprotocol Service
  "These options are mostly for the service rather than the resource. Although it is possible to provide methods, these options are mostly to secure and protected the service, via access control and rate limiting."
  (service-available? [_ ctx] "Return whether the service is available. Supply a function which can return a deferred, if necessary.")
  (interpret-service-available [_] "Whether the result of service-available? means that the service is actually available")
  (retry-after [_] "Given the result of service-available? implies the service is unavailable, interpret the result as a retry-after header value")

  (request-uri-too-long? [_ uri])

  (status [_ ctx] "Override the response status")
  (headers [_ ctx] "Override the response headers")

  (authorize [_ ctx] "Authorize the request. When truthy, authorization is called with the value and used as the :authorization entry of the context, otherwise assumed unauthorized.")
  (authorization [o] "Given the result of an authorize call, a truthy value will be added to the context.")
  (allow-origin [_ ctx] "If another origin is allowed, return the the value of the Access-Control-Allow-Origin header to be set on the response"))

(extend-protocol Service
  Boolean
  (service-available? [b ctx] b)
  (interpret-service-available [b] b)
  (retry-after [b] nil)
  (request-uri-too-long? [b _] b)
  (authorize [b ctx] b)
  (authorization [b] nil)
  (allow-origin [b _] (when b "*"))

  clojure.lang.Fn
  (service-available? [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(service-available? % ctx))
        (service-available? res ctx))))

  (request-uri-too-long? [f uri] (request-uri-too-long? (f uri) uri))

  #_(body [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(body % ctx))
        (body res ctx))))

  #_(last-modified [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))

  (authorize [f ctx] (f ctx))
  (allow-origin [f ctx] (f ctx))
  (status [f ctx] (f ctx))

  manifold.deferred.Deferred
  (interpret-service-available [v]
    (d/chain v (fn [v] (interpret-service-available v))))

  String
  #_(format-event [ev] [(format "data: %s\n" ev)])

  Number
  (service-available? [n _] n)
  (interpret-service-available [_] false)
  (retry-after [n] n)
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))
  (status [n ctx] n)

  java.util.Map
  (headers [m _] m)
  #_(format-event [ev] ev)

  nil
  ;; These represent the handler defaults, all of which can be
  ;; overridden by providing non-nil arguments
  (service-available? [_ _] true)
  (request-uri-too-long? [_ uri]
    (request-uri-too-long? 4096 uri))
  (status [_ _] nil)
  (headers [_ _] nil)
  #_(allow-origin [_ _] nil)

  #_ReadPort
  #_(body [port ctx] (->source port))

  Object
  (authorization [o] o))
