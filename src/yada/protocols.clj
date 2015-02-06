;; Copyright © 2015, JUXT LTD.

(ns yada.protocols
  (:require
   [manifold.deferred :as d]))

(defprotocol Callbacks
  (service-available? [_] "Return whether the service is available")
  (known-method? [_ method])
  (request-uri-too-long? [_ uri])
  (allowed-method? [_ method])
  (resource [_ opts] "Return the resource. Typically this is just the resources's meta-data and does not include the body.")
  (entity [_ resource] "Given a resource, return the entity (a data model). For example, for a customer resource, return the customer data for that customer.")
  (body [_ entity content-type] "Return the representation, in the given content-type, of a given entity")
  (produces [_] "Return the content-types, as a set, that the resource can produce"))

(extend-protocol Callbacks
  Boolean
  (service-available? [b] [b {}])
  (known-method? [b method] [b {}])
  (request-uri-too-long? [b _] [b {}])
  (allowed-method? [b _] [b {}])
  (resource [b opts] (when b {}))

  clojure.lang.Fn
  (service-available? [f] (let [res (f)]
                            (if (d/deferrable? res)
                              (d/chain (service-available? @res))
                              (service-available? res))))
  (known-method? [f method] (known-method? (f method) method))
  (request-uri-too-long? [f uri] (request-uri-too-long? (f uri) uri))
  (allowed-method? [f method] (allowed-method? (f method) method))
  (resource [f opts] (let [res (f opts)]
                       (if (d/deferrable? res)
                         (d/chain (resource res opts))
                         (resource res opts))))

  (entity [f resource] (f resource))
  (body [f entity content-type] (f entity content-type))
  (produces [f] (f))

  String
  (body [s _ _] s)

  Number
  (service-available? [n] [false {:headers {"retry-after" n}}])
  (request-uri-too-long? [n uri]
    (request-uri-too-long? (> (.length uri) n) uri))

  java.util.Set
  (known-method? [set method]
    [(contains? set method) {}])
  (allowed-method? [set method]
    [(contains? set method) {}])
  (produces [set] set)

  clojure.lang.Keyword
  (known-method? [k method]
    (known-method? #{k} method))
  (allowed-method? [k method]
    (allowed-method? #{k} method))

  java.util.Map
  (resource [m _] m)
  (entity [m resource]
    (assoc m :resource resource))
  (body [m entity content-type]
    ;; Maps indicate keys are exact content-types
    ;; For matching on content-type, use a vector of vectors (TODO)
    (when-let [delegate (get m content-type)]
      (body delegate entity content-type)))

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
  (allowed-method? [_ method]
    (allowed-method? #{:get :head} method))
  (resource [_ opts] nil)
  (entity [_ resource]
    nil)
  (body [_ entity content-type]
    nil)
  (produces [_]
    #{"text/html"})


  )
