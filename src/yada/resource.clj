;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d])
  (:import [clojure.core.async.impl.protocols ReadPort]
           [java.io File InputStream]
           [java.util Date]))

(defprotocol Resourceful
  "A protocol for finding resources from yada's initial resource argument"
  (resource [_ ctx]))

(extend-protocol Resourceful
  clojure.lang.Fn
  (resource [f ctx]
    (let [res (f ctx)]
      (if (d/deferrable? res)
        (d/chain res #(resource % ctx))
        (resource res ctx))))
  Object
  (resource [o ctx] o)

  nil
  (resource [_ ctx] nil))

(defprotocol Resource
  "A protocol for describing a resource: where it is, when it was last
  updated, how to change it, etc. "

  (exists? [_ ctx] "Whether the resource actually exists")

  (last-modified [_ ctx] "Return the date that the resource was last modified.")

  (produces [_] "Return the mime types that can be produced from this resource. The result is request-context independent, suitable for consumption by introspectng tools such as swagger.")

  (content-length [_ ctx] "Return the content length, if possible.")

  (get-state [_ content-type ctx] "Return the state, formatted to a representation of the given content-type and charset. Returning nil results in a 404.")

  (put-state! [_ content content-type ctx] "Overwrite the state with the data. To avoid inefficiency in abstraction, satisfying types are required to manage the parsing of the representation in the request body. If a deferred is returned, the HTTP response status is set to 202")

  (post-state! [_ ctx] "Insert a new sub-resource. See write! for semantics.")

  (delete-state! [_ ctx] "Delete the state. If a deferred is returned, the HTTP response status is set to 202"))

(extend-protocol Resource
  clojure.lang.Fn
  (last-modified [f ctx]
    (let [res (f)]
      (if (d/deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))

  Number
  (last-modified [l _] (Date. l))

  String
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
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_ _] nil)
  (get-state [_ content-type ctx] nil)
  (content-length [_ _] nil)

  Object
  (last-modified [_ _] nil)
  (content-length [_ _] nil)

  )
