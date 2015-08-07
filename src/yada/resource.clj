;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d]
            [yada.charset :refer (to-charset-map)]
            [yada.mime :as mime]
            [yada.util :refer (deferrable?)])
  (:import [clojure.core.async.impl.protocols ReadPort]
           [java.io File InputStream]
           [java.util Date]))

;; Resource protocols

(defprotocol ResourceCoercion
  (make-resource [_] "Coerce to a resource. Often, resources need to be
  coerced rather than simply extending types with the Resource
  protocol. We can exploit the time of coercion to know the time of
  birth for the resource, which supports time-based conditional
  requests. For example, a simple StringResource is immutable, so by
  knowing the time of construction, we can precisely state its
  Last-Modified-Date."))

(extend-protocol ResourceCoercion
  clojure.lang.Fn
  (make-resource [f]
    ;; In the case of a function, we assume the function is dynamic
    ;; (taking the request context), so we return it ready for its
    ;; default Resource implementation (above)
    f)

  Object
  (make-resource [o] o)

  nil
  (make-resource [_] nil))

;; Fetch

(defprotocol ResourceFetch
  (fetch [this ctx] "Fetch the resource, such that questions can be
  answered about it. Anything you return from this function will be
  available in the :resource entry of ctx and will form the type that
  will be used to dispatch other functions in this protocol. You can
  return a deferred if necessary (indeed, you should do so if you have
  to perform some IO in this function). Often, you will return 'this',
  perhaps augmented with some additional state. Sometimes you will
  return something else."))

;; Fetch happens before negotiation, so must only return resource data,
;; nothing to do with the representation itself. Negotiation information
;; will not be present in the context, which is provided primarily to
;; give the resource fetch access to the Ring request and it's own
;; resource definition.

(extend-protocol ResourceFetch
  nil ; The user has not elected to specify a resource, that's fine (and common)
  (fetch [_ ctx] nil)
  Object
  (fetch [o ctx] o))

(defprotocol ResourceAllowedMethods
  "Optional protocol for resources to indicate which methods are allowed."
  (allowed-methods [_]
    "Return the allowed methods. Context-agnostic - can be introspected
    by tools (e.g. swagger)"))

(extend-protocol ResourceAllowedMethods
  Object
  (allowed-methods [_] #{:get})
  nil
  (allowed-methods [_] nil))

;; Existence

(defprotocol ResourceExistence
  "Optional protocol for a resource to indicate its
  existence. Non-existent resources cause 404 responses."
  (exists? [_ ctx]
    "Whether or not the resource actually exists. Is context sensitive,
    since existence can often depend on request context, such as
    parameters. Therefore the context is given as an argument. Return
    truthy if the resource exists. Can return a deferred value."))

(extend-protocol ResourceExistence
  Object (exists? [_ ctx] true)
  nil (exists? [_ ctx] false))

;; Modification

(defprotocol ResourceModification
  "Optional protocol for a resource to indicate when its state was last
  modified."
  (last-modified [_ ctx]
    "Return the date that the resource was last modified. Is context
    sensitive, the context is given as an argument. Can return a
    deferred value."))

(extend-protocol ResourceModification
  clojure.lang.Fn
  (last-modified [_ ctx] nil)
  java.io.File
  (last-modified [f ctx] (Date. (.lastModified f)))
  ;; last-modified of 'nil' means we don't consider last-modified
  nil
  (last-modified [_ _] nil))

;; Negotiation

(defprotocol ResourceRepresentations
  ;; Context-agnostic
  (representations [_] "Declare the resource's capabilities. Return a
  sequence, each item of which specifies the methods, content-types,
  charsets, languages and encodings that the resource is capable
  of. Each of these dimensions may be specified as a set, meaning 'one
  of'. Drives the default negotiation algorithm."))

;; One idea is to combine 'static' representations with 'dynamic'
;; ones. Swagger can use the static representations, but these can be
;; augmented by content-specific ones. DirectoryResource, for example,
;; would be able to provide some static representations to explain to
;; swagger that it could be posted to with particular content, but would
;; change these on a request containing a path-info. The dynamic version
;; would have access to the static result, via the context, and could
;; choose how to augment these (override completely, concat, etc.)

(extend-protocol ResourceRepresentations
  clojure.lang.PersistentVector (representations [v] v)
  nil (representations [_] nil))

(def default-platform-charset (.name (java.nio.charset.Charset/defaultCharset)))

(def platform-charsets
  (->> (concat
        [(to-charset-map default-platform-charset)]
        (map #(assoc % :weight 0.9) (map to-charset-map (keys (java.nio.charset.Charset/availableCharsets)))))
       ;; Tune down the number of charsets to manageable level by
       ;; excluding those prefixed by x- and 'IBM'.
       (filter (comp not (partial re-matches #"(x-|IBM).*") :alias))
       set))

(defprotocol ResourceParameters
  "Declare the resource's parameters"
  (parameters [_] "Return the parameters, by method. Must not return a deferred value."))

(defprotocol ResourceEntityTag
  "Entity tags."
  (etag [_ ctx] "Return the entity tag of a resource. If returning a
  string that is used as the etag, which should incorporate
  the (potentially variable) representation. Representation data is
  available in the ctx at [:response :representation], and this should
  be used if returning a string since the etag returned for two
  representations that differ only in representation should not be
  identical. Return something other than a string and the representation
  will be included in the computation of the etag."))


(defprotocol ETagResult
  "The etag function may return a string, which becomes the final ETag
  response header. Alternatively, it may return other results, which
  must be coerced into the final ETag response header value."
  (coerce-etag-result [_] "Coerce the result into a string"))

(extend-protocol ETagResult
  String
  (coerce-etag-result [s] s)
  Object
  (coerce-etag-result [o] (str (hash o))))
