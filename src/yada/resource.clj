;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:refer-clojure :exclude [methods])
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d]
            [yada.charset :refer (to-charset-map)]
            [yada.mime :as mime]
            [yada.util :refer (deferrable?)])
  (:import [clojure.core.async.impl.protocols ReadPort]
           [java.io File InputStream]
           [java.util Date]))

(defprotocol ResourceConstructor
  (make-resource [_] "Make a resource. Often, resources need to be
  constructed rather than simply extending types with the Resource
  protocol. For example, we sometimes need to know the exact time that a
  resource is constructed, to support time-based conditional
  requests. For example, a simple StringResource is immutable, so by
  knowing the time of construction, we can precisely state its
  Last-Modified-Date."))

(extend-protocol ResourceConstructor
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

(defprotocol ResourceFetch
  (fetch [this ctx] "Fetch the resource, such that questions can be
  answered about it. Anything you return from this function will be
  available in the :resource entry of ctx and will form the type that
  will be used to dispatch other functions in this protocol. You can
  return a deferred if necessary (indeed, you should do so if you have
  to perform some IO in this function). Often, you will return 'this',
  perhaps augmented with some additional state. Sometimes you will
  return something else."))

(defprotocol Resource
  "A protocol for describing a resource: where it is, when it was last
  updated, how to change it, etc. A resource may hold its state, or be
  able to educe the state on fetch or during the request call."

  ;; Context-agnostic - can be introspected by tools (e.g. swagger)
  (methods [_]
    "Return the allowed methods.")

  ;; Context-sensitive
  (exists? [_ ctx]
    "Whether the resource actually exists. Can return a deferred value.")

  (last-modified [_ ctx]
    "Return the date that the resource was last modified. Can return a
    deferred value."))

(extend-protocol Resource
  clojure.lang.Fn
  (methods [_] #{:get :head})
  ;; We assume the resource exists, the request can force a 404 by
  ;; returning nil.
  (exists? [_ ctx] true)
  (last-modified [_ ctx] nil)

  java.io.File
  (last-modified [f ctx]
    (Date. (.lastModified f)))

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (methods [_] nil)
  (last-modified [_ _] nil))

;; Fetch

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

;; Negotiation

(defprotocol Negotiable
  (negotiate [_ ctx] "Negotiate the resource's representations. Return a
  yada.negotiation/NegotiationResult. Optional protocol, falls back to
  default negotiation."))

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
  (set
   (concat
    [(to-charset-map default-platform-charset)]
    (map #(assoc % :weight 0.9) (map to-charset-map (keys (java.nio.charset.Charset/availableCharsets)))))))

(defprotocol ResourceParameters
  "Declare the resource's parameters"
  (parameters [_] "Return the parameters, by method. Must not return a deferred value."))
