;; Copyright © 2015, JUXT LTD.

(ns yada.protocols)

(defprotocol Callbacks
  (service-available? [_] "Return whether the service is available")
  (known-method? [_ method])
  (request-uri-too-long? [_ uri])
  (allowed-method? [_ method swagger-ops])
  (find-resource [_ opts])
  (entity [_ resource])
  (body [_ entity content-type]))

(extend-protocol Callbacks
  Boolean
  (service-available? [b] b)
  (request-uri-too-long? [b _] b)
  (allowed-method? [b _ _] b)
  (find-resource [b opts] (when b {}))

  clojure.lang.Fn
  (service-available? [f] (f))
  (known-method? [f method] (f method))
  (request-uri-too-long? [f uri] (f uri))
  (allowed-method? [f method op] (f method op))
  (find-resource [f opts] (f opts))
  (entity [f resource] (f resource))
  (body [f entity content-type] (f entity content-type))

  String
  (entity [s _] s)
  (body [s _ _] s)

  Number
  (request-uri-too-long? [n uri]
    (> (.length uri) n))

  java.util.Set
  (known-method? [set method]
    (contains? set method))
  (allowed-method? [set method op]
    (contains? set method))

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
