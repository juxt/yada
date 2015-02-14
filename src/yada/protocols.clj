;; Copyright © 2015, JUXT LTD.

(ns yada.protocols
  (:require
   [manifold.deferred :as d]))

(defprotocol Callbacks
  (service-available? [_] "Return whether the service is available")
  (known-method? [_ method])
  (request-uri-too-long? [_ uri])
  (resource [_ opts] "Return the resource. Typically this is just the resources's meta-data and does not include the body.")
  (body [_ ctx] "Return a representation of the resource. See yada documentation for the structure of the ctx argument.")
  (produces [_] "Return the content-types, as a set, that the resource can produce")
  (produces-from-body [_] "If produces yields nil, try to extract from body")
  (status [_] "Override the response status")
  (headers [_] "Override the response headers"))

(extend-protocol Callbacks
  Boolean
  (service-available? [b] [b {}])
  (known-method? [b method] [b {}])
  (request-uri-too-long? [b _] [b {}])
  (resource [b opts] (when b {}))

  clojure.lang.Fn
  (service-available? [f]
    (let [res (f)]
      (if (d/deferrable? res)
        (d/chain (service-available? @res))
        (service-available? res))))

  (known-method? [f method] (known-method? (f method) method))
  (request-uri-too-long? [f uri] (request-uri-too-long? (f uri) uri))

  (resource [f opts]
    (let [res (f opts)]
      (if (d/deferrable? res)
        (d/chain (resource @res opts))
        (resource res opts))))

  (body [f ctx]
    ;; body is not called recursively
    (f ctx))

  (produces [f] (f))
  (produces-from-body [f] nil)

  String
  (body [s _] s)
  (produces-from-body [s] nil)

  Number
  (service-available? [n] [false {:headers {"retry-after" n}}])
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))
  (status [n] n)

  java.util.Set
  (known-method? [set method]
    [(contains? set method) {}])
  (produces [set] set)

  clojure.lang.Keyword
  (known-method? [k method]
    (known-method? #{k} method))

  java.util.Map
  (resource [m _] m)
  (body [m ctx]
    ;; Maps indicate keys are exact content-types
    ;; For matching on content-type, use a vector of vectors (TODO)
    (when-let [delegate (get m (get-in ctx [:response :content-type]))]
      (body delegate ctx)))
  (produces-from-body [m] (keys m))
  (headers [m] m)

  clojure.lang.PersistentVector
  (produces [v] (produces (set v)))

  nil
  ;; These represent the handler defaults, all of which can be
  ;; overridden by providing non-nil arguments
  (service-available? [_] true)
  (known-method? [_ method]
    (known-method? #{:get :put :post :delete :options :head} method))
  (request-uri-too-long? [_ uri]
    (request-uri-too-long? 4096 uri))
  (resource [_ opts] nil)
  (body [_ _] nil)
  (produces [_] nil)
  (produces-from-body [_] nil)
  (status [_] nil)
  (headers [_] nil))
