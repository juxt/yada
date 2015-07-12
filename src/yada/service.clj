;; Copyright © 2015, JUXT LTD.

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

  (known-method? [_ method] "Whether the method is known. Supply a function which can return a deferred, if necessary.")

  (request-uri-too-long? [_ uri])

  (allowed-methods [_] "Return a set of the allowed methods. Must be determined at compile time (for purposes of introspection by tools). No async support.")

  (exists? [_ ctx] "Return whether the resource exists")
  (last-modified [_ ctx] "Return the last modified time, as a date or long, of the resource.")
  (body [_ ctx] "Return the response body. Supply a function which can return a deferred, if necessary.")

  (produces [_] [_ ctx] "Return the content-types that the service can produce. Deprecated.")
  (produces-charsets [_ ctx] "Return the charsets that the service can produce. Deprecated.")

  (status [_ ctx] "Override the response status")
  (headers [_ ctx] "Override the response headers")

  (put! [_ ctx] "Implement a service's PUT method")
  (post! [_ ctx] "Implement a service's POST method")
  (delete! [_ ctx] "Implement a service's DELETE method")
  (interpret-post-result [_ ctx] "Return the request context, according to the result of post")

  (trace [_ req ctx] "Intercept tracing, providing an alternative implementation, return a Ring response map.")

  (authorize [_ ctx] "Authorize the request. When truthy, authorization is called with the value and used as the :authorization entry of the context, otherwise assumed unauthorized.")
  (authorization [o] "Given the result of an authorize call, a truthy value will be added to the context.")

  (format-event [_] "Format an individual event (server sent events)")

  (allow-origin [_ ctx] "If another origin is allowed, return the the value of the Access-Control-Allow-Origin header to be set on the response"))

(extend-protocol Service
  Boolean
  (service-available? [b ctx] b)
  (interpret-service-available [b] b)
  (retry-after [b] nil)
  (known-method? [b method] b)
  (request-uri-too-long? [b _] b)
  (post [b ctx] b)
  (interpret-post-result [b ctx]
    (if b ctx (throw (ex-info "Failed to process POST" {}))))
  (authorize [b ctx] b)
  (authorization [b] nil)
  (allow-origin [b _] (when b "*"))
  (exists? [b _] b)

  clojure.lang.Fn
  (service-available? [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(service-available? % ctx))
        (service-available? res ctx))))

  (known-method? [f method]
    (let [res (known-method? (f method) method)]
      (if (deferrable? res)
        (d/chain res #(known-method? % method))
        res)))

  (request-uri-too-long? [f uri] (request-uri-too-long? (f uri) uri))

  (body [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(body % ctx))
        (body res ctx))))

  (last-modified [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))

  (produces [f] (produces (f)))
  (produces [f ctx] (produces (f) ctx))

  (post! [f ctx]
    (f ctx))

  (authorize [f ctx] (f ctx))

  (allow-origin [f ctx] (f ctx))
  (status [f ctx] (f ctx))

  manifold.deferred.Deferred
  (interpret-service-available [v]
    (d/chain v (fn [v] (interpret-service-available v))))

  File
  (last-modified [f ctx] (new Date (.lastModified f)))

  String
  (body [s _] s)
  (interpret-post-result [s ctx]
    (assoc-in ctx [:response :body] s))
  (format-event [ev] [(format "data: %s\n" ev)])
  (produces [s] [s])
  (produces [s ctx] [s])
  (produces-charsets [s ctx] [s])
  (post! [s ctx] s)

  MediaTypeMap
  (produces [m] [m])
  (produces [m _] [m])

  Number
  (service-available? [n _] n)
  (interpret-service-available [_] false)
  (retry-after [n] n)
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))
  (status [n ctx] n)

  Long
  (last-modified [l ctx] (Date. l))
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))

  java.util.Set
  (known-method? [set method]
    (contains? set method))
  (allowed-methods [s] s)
  (produces [s] s)
  (produces [s ctx] s)

  clojure.lang.Keyword
  (known-method? [k method]
    (known-method? #{k} method))

  java.util.Map
  (body [m ctx]
    ;; Maps indicate keys are exact content-types
    ;; For matching on content-type, use a vector of vectors (TODO)
    (when-let [delegate (get m (get-in ctx [:response :content-type]))]
      (body delegate ctx)))
  (headers [m _] m)
  (post! [m ctx] m)
  (interpret-post-result [m ctx]
    ;; TODO: Factor out hm (header-merge) so it can be tested independently
    (letfn [(hm [x y]
              (cond
                (and (nil? x) (nil? y)) nil
                (or (nil? x) (nil? y)) (or x y)
                (and (coll? x) (coll? y)) (concat x y)
                (and (coll? x) (not (coll? y))) (concat x [y])
                (and (not (coll? x)) (coll? y)) (concat [x] y)
                :otherwise (throw (ex-info "Unexpected headers case" {:x x :y y}))))]
      (cond-> ctx
        (:status m) (assoc-in [:response :status] (:status m))
        (:headers m) (update-in [:response :headers] #(merge-with hm % (:headers m)))
        (:body m) (assoc-in [:response :body] (:body m)))))

  (format-event [ev] ev)

  clojure.lang.PersistentVector
  (produces [v] v)
  (produces [v ctx] v)
  (body [v ctx] v)
  (allowed-methods [v] v)

  java.util.List
  (allowed-methods [v] v)

  nil
  ;; These represent the handler defaults, all of which can be
  ;; overridden by providing non-nil arguments
  (service-available? [_ _] true)
  (request-uri-too-long? [_ uri]
    (request-uri-too-long? 4096 uri))
  (exists? [_ ctx] nil)
  (last-modified [_ ctx] nil)
  (body [_ _] nil)
  (post [_ _] nil)
  (produces [_] nil)
  (produces [_ ctx] nil)
  (produces-charsets [_ ctx] nil)
  (status [_ _] nil)
  (headers [_ _] nil)
  (interpret-post-result [_ ctx] ctx)
  (allow-origin [_ _] nil)

  ReadPort
  (body [port ctx] (->source port))

  Object
  (authorization [o] o)

  )
