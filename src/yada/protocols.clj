;; Copyright © 2015, JUXT LTD.

(ns yada.protocols
  (:require
   [manifold.deferred :as d]
   [clojure.tools.logging :refer :all]))

(defprotocol Callbacks
  (service-available? [_] "Return whether the service is available")
  (known-method? [_ method])
  (request-uri-too-long? [_ uri])
  (resource [_ req] "Return the resource. Typically this is just the resources's meta-data and does not include the body.")
  (state [_ ctx] "Return the state, if not available in the resource.")
  (last-modified [_ ctx] "Return the date that the resource was last modified.")
  (body [_ ctx] "Return a representation of the resource. See yada documentation for the structure of the ctx argument.")
  (produces [_] "Return the content-types, as a set, that the resource can produce")
  (produces-from-body [_] "If produces yields nil, try to extract from body")
  (status [_] "Override the response status")
  (headers [_] "Override the response headers")
  (post [_ ctx] "POST to the resource")
  (interpret-post-result [_ ctx] "Return the request context, according to the result of post")

  (authorize [_ ctx] "Authorize the request. When truthy, authorization is called with the value and used as the :authorization entry of the context, otherwise assumed unauthorized.")
  (authorization [o] "Given the result of an authorize call, a truthy value will be added to the context.")

  (events [e ctx] "Provide server-sent events")
  (format-event [e] "Format an individual event")
  )

(extend-protocol Callbacks
  Boolean
  (service-available? [b] [b {}])
  (known-method? [b method] [b {}])
  (request-uri-too-long? [b _] [b {}])
  (resource [b req] (if b {} false))
  (interpret-post-result [b ctx]
    (if b ctx (throw (ex-info "Failed to process POST" {}))))
  (authorize [b ctx] b)
  (authorization [b] nil)

  clojure.lang.Fn
  (service-available? [f]
    (let [res (f)]
      (if (d/deferrable? res)
        (d/chain res #(service-available? %))
        (service-available? res))))

  (known-method? [f method] (known-method? (f method) method))
  (request-uri-too-long? [f uri] (request-uri-too-long? (f uri) uri))

  (resource [f req]
    (let [res (f req)]
      (if (d/deferrable? res)
        (d/chain res #(resource % req))
        (resource res req))))

  (state [f ctx]
    (let [res (f ctx)]
      (if (d/deferrable? res)
        (d/chain res #(state % ctx))
        (state res ctx))))

  (last-modified [f ctx]
    (let [res (f ctx)]
      (if (d/deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))

  (body [f ctx]
    ;; body is not called recursively
    (f ctx))

  (produces [f] (f))
  (produces-from-body [f] nil)

  (post [f ctx]
    (f ctx))

  (authorize [f ctx] (f ctx))

  (events [f ctx] (f ctx))

  String
  (body [s _] s)
  (produces-from-body [s] nil)
  (interpret-post-result [s ctx]
    (assoc ctx :location s))
  (format-event [ev] [(format "data: %s\n" ev)])

  Number
  (service-available? [n] [false {:headers {"retry-after" n}}])
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))
  (status [n] n)
  (last-modified [l _] (java.util.Date. l))

  java.util.Set
  (known-method? [set method]
    [(contains? set method) {}])
  (produces [set] set)

  clojure.lang.Keyword
  (known-method? [k method]
    (known-method? #{k} method))

  java.util.Map
  (resource [m _] m)
  (state [m _] m)
  (body [m ctx]
    ;; Maps indicate keys are exact content-types
    ;; For matching on content-type, use a vector of vectors (TODO)
    (when-let [delegate (get m (get-in ctx [:response :content-type]))]
      (body delegate ctx)))
  (produces-from-body [m] (keys m))
  (headers [m] m)
  (interpret-post-result [m _] m)
  (format-event [ev] )

  clojure.lang.PersistentVector
  (produces [v] (produces (set v)))
  (events [v ctx] v)

  java.util.Date
  (last-modified [d _] d)

  nil
  ;; These represent the handler defaults, all of which can be
  ;; overridden by providing non-nil arguments
  (service-available? [_] true)
  (known-method? [_ method]
    (known-method? #{:get :put :post :delete :options :head} method))
  (request-uri-too-long? [_ uri]
    (request-uri-too-long? 4096 uri))
  (resource [_ _] nil)
  (state [_ _] nil)
  (body [_ _] nil)
  (produces [_] nil)
  (produces-from-body [_] nil)
  (status [_] nil)
  (headers [_] nil)

  Object
  (authorization [o] o)

  )
