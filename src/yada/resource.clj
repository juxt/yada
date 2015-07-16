;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:refer-clojure :exclude [methods])
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d]
            [yada.charset :refer (to-charset-map)]
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

  #_Object
  #_(make-resource [o] o)

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
  (methods [_] "Return the allowed methods.")
  (parameters [_] "Return the parameters, by method. Must not return a deferred value.")

  ;; Context-sensitive
  (exists? [_ ctx] "Whether the resource actually exists. Can return a deferred value.")

  (last-modified [_ ctx] "Return the date that the resource was last
  modified. Can return a deferred value.")

  (request [_ method ctx] "Perform request. Context contains
  content-type, charset, language, content-encoding etc. in
  the :response map. Method as keyword is in context's :method entry,
  but is provided as a parameter for convenience only. The returned
  value is interpreted according to the its type, and the request
  method. Side-effects are permissiable. Can return a deferred result.

  GET: Return the state. Can be formatted to a representation of the
  given media-type and charset. Returning nil results in a 404. Get the
  charset from the context [:request :charset], if you can support
  different charsets. A nil charset at [:request :charset] means the
  user-agent can support all charsets, so just pick one. If you don't
  return a String, a representation will be attempted from whatever you
  do return.

  PUT: Overwrite the state with the data. To avoid inefficiency in
  abstraction, satisfying types are required to manage the parsing of
  the representation in the request body. If a deferred is returned, the
  HTTP response status is set to 202.

  POST: Post the new data. Return a result. If a Ring response map is
  returned, it is returned to the client. If a function can be
  reeturned, it is invoked with the context as the only parameter. If a
  string is returned, it is assumed to be the body. See
  yada.methods/PostResult for full details of what can be returned.

  DELETE: Delete the state. If a deferred is returned, the HTTP response
  status is set to 202"))

(extend-protocol Resource
  clojure.lang.Fn
  (methods [_] #{:get :head})
  ;; TODO: Many resources don't have parameters - let's put this in its
  ;; own protocol (ResourceParameters)
  (parameters [_] nil)
  ;; We assume the resource exists, the request can force a 404 by
  ;; returning nil.
  (exists? [_ ctx] true)
  (last-modified [_ ctx] nil)
  (request [f method ctx] (f ctx))

  java.io.File
  (last-modified [f ctx]
    (Date. (.lastModified f)))

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (methods [_] nil)
  (parameters [_] nil)
  (last-modified [_ _] nil)
  (request [f method ctx] nil))

;; Fetch

;; Fetch happens before negotiation, so must only return resource data,
;; nothing to do with the representation itself. Negotiation information
;; will not be present in the context, which is provided primarily to
;; give the resource fetch access to the Ring request and it's own
;; resource definition.

(extend-protocol ResourceFetch
  #_clojure.lang.Fn
  #_(fetch [f ctx]
    (let [res (f ctx)]
      ;; We call make-resource on dynamic fetch functions, to ensure the
      ;; result they return are treated just the same as if they were
      ;; presented statically to the yada function.  Fetch is complected
      ;; two ideas here. The first is the loading of
      ;; state/meta-state. The second is allowing us to use functions in
      ;; place of resources. Things seem to work OK with this complected
      ;; design, but alarm bells are beginning to sound...
      (if (deferrable? res)
        (d/chain res #(make-resource (fetch % ctx)))
        (make-resource (fetch res ctx)))))
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
  clojure.lang.PersistentVector
  (representations [v] v)

  nil
  (representations [_] nil))

(def platform-charsets
  (concat
   [(to-charset-map (.name (java.nio.charset.Charset/defaultCharset)))]
   (map #(assoc % :weight 0.9) (map to-charset-map (keys (java.nio.charset.Charset/availableCharsets))))))
