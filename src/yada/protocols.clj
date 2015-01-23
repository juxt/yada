;; Copyright © 2015, JUXT LTD.

(ns yada.protocols)

(defprotocol Callbacks
  (service-available? [_] "Return whether the service is available")
  (known-method? [_ method])
  (request-uri-too-long? [_ uri])
  (allowed-method? [_ method])
  (find-resource [_ opts] "Return the resource's meta-data")
  (entity [_ resource] "Given a resource's metadata, return the entity (a data model). For example, for a customer resource, return the customer data for that customer.")
  (body [_ entity content-type] "Return the representation, in the given content-type, of a given entity")
  (produces [_] "Return the content-types, as a set, that the resource can produce"))

(extend-protocol Callbacks
  Boolean
  (service-available? [b] b)
  (request-uri-too-long? [b _] b)
  (allowed-method? [b _] b)
  (find-resource [b opts] (when b {}))

  clojure.lang.Fn
  (service-available? [f] (f))
  (known-method? [f method] (f method))
  (request-uri-too-long? [f uri] (f uri))
  (allowed-method? [f method] (f method))
  (find-resource [f opts] (f opts))
  (entity [f resource] (f resource))
  (body [f entity content-type] (f entity content-type))
  (produces [f] (f))

  String
  (entity [s _] s)
  (body [s _ _] s)

  Number
  (request-uri-too-long? [n uri]
    (> (.length uri) n))

  java.util.Set
  (known-method? [set method]
    (contains? set method))
  (allowed-method? [set method]
    (contains? set method))
  (produces [set] set)

  java.util.Map
  (find-resource [m opts] m)
  (entity [m resource] m)
  (body [m entity content-type]
    ;; Maps indicate keys are exact content-types
    ;; For matching on content-type, use a vector of vectors (TODO)
    (when-let [delegate (get m content-type)]
      (body delegate entity content-type)))

  nil
  ;; TODO: is this needed?
  (find-resource [_ opts] nil)
  )
