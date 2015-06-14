;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d]
            [yada.util :refer (deferrable?)])
  (:import [clojure.core.async.impl.protocols ReadPort]
           [java.io File InputStream]
           [java.util Date]))

(defprotocol Resource
  "A protocol for describing a resource: where it is, when it was last
  updated, how to change it, etc. A resource may hold its state, or be able to educe the state on demand (via get-state)."

  (fetch [_ ctx] "Fetch the resource state and/or metadata, such that questions can be answered about it. Anything you return from this function will be available in the :resource entry of ctx passed to other functions in this protocol. You can return a deferred if necessary (indeed, you should do so if you have to perform some IO in this function)")

  (exists? [_ ctx] "Whether the resource actually exists")

  (last-modified [_ ctx] "Return the date that the resource was last modified.")

  (produces [_] [_ ctx]
    "Return the mime types that can be produced from this resource. The first form is request-context independent, suitable for up-front consumption by introspectng tools such as swagger. The second form can be more sensitive to the request context.")

  (content-length [_ ctx] "Return the content length, if possible.")

  (get-state [_ content-type ctx] "Return the state, formatted to a representation of the given content-type and charset. Returning nil results in a 404.")

  (put-state! [_ content content-type ctx] "Overwrite the state with the data. To avoid inefficiency in abstraction, satisfying types are required to manage the parsing of the representation in the request body. If a deferred is returned, the HTTP response status is set to 202")

  (post-state! [_ ctx] "Insert a new sub-resource. See write! for semantics.")

  (delete-state! [_ ctx] "Delete the state. If a deferred is returned, the HTTP response status is set to 202"))

(extend-protocol Resource
  clojure.lang.Fn
  (fetch [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(fetch % ctx))
        (fetch res ctx))))
  (last-modified [f ctx]
    (let [res (f)]
      (if (deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))

  Number
  (last-modified [l _] (Date. l))

  String
  (fetch [s ctx] s)
  (exists? [s ctx] true)
  (last-modified [s _] nil)
  (get-state [s content-type ctx] s)
  ;; Without attempting to actually parse it (which isn't completely
  ;; impossible) we're not able to guess the content-type of this
  ;; string, so we return nil.
  (produces [s] nil)
  (content-length [_ _] nil)

  Date
  (last-modified [d _] d)

  nil
  (fetch [_ ctx] nil)
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_ _] nil)
  (get-state [_ content-type ctx] nil)
  (content-length [_ _] nil)

  Object
  (fetch [o ctx] o)
  (last-modified [_ _] nil)
  (content-length [_ _] nil)

  )


(defprotocol ResourceConstructor
  (make-resource [_] "Make a resource. Often, resources need to be constructed rather than simply extending types with the Resource protocol. For example, we sometimes need to know the exact time that a resource is constructed, to support time-based conditional requests. For example, a simple StringResource is immutable, so by knowing the time of construction, we can precisely state its Last-Modified-Date."))

(extend-protocol ResourceConstructor
  clojure.lang.Fn
  (make-resource [f]
    ;; In the case of a function, we assume thee function is dynamic
    ;; (taking the request context), so we return it ready for its
    ;; default Resource implementation (above)
    f)
  Object
  (make-resource [o] o)
  nil
  (make-resource [_] nil))
