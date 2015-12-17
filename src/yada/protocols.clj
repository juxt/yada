;; Copyright © 2015, JUXT LTD.

(ns yada.protocols
  (:import
   [java.io File]
   [java.util Date]
   [clojure.lang APersistentMap]))

;; Resource protocols

(defprotocol ResourceCoercion
  (as-resource [_] "Coerce to a resource. Often, resources need to be
  coerced rather than extending types directly with the Resource
  protocol. We can exploit the time of coercion to know the time of
  birth for the resource, which supports time-based conditional
  requests. For example, a simple StringResource is immutable, so by
  knowing the time of construction, we can precisely state its
  Last-Modified-Date."))

(defprotocol ETag
  "The version function returns material that becomes the ETag response
  header. This is left open for extension. The ETag must differ between
  representations, so the representation is given in order to be used in
  the algorithm. Must always return a string (to aid comparison with the
  strings the client will present on If-Match, If-None-Match."
  (to-etag [_ rep]))

(extend-protocol ETag
  Object
  (to-etag [o rep]
    (str (hash {:value o :representation rep})))
  nil
  (to-etag [o rep] nil))
