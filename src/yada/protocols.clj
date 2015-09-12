;; Copyright © 2015, JUXT LTD.

(ns yada.protocols
  (:import
   [java.io File]
   [java.util Date]))

;; Resource protocols

(defprotocol ResourceCoercion
  (as-resource [_] "Coerce to a resource. Often, resources need to be
  coerced rather than extending types directly with the Resource
  protocol. We can exploit the time of coercion to know the time of
  birth for the resource, which supports time-based conditional
  requests. For example, a simple StringResource is immutable, so by
  knowing the time of construction, we can precisely state its
  Last-Modified-Date."))

(extend-protocol ResourceCoercion
  Object
  (as-resource [o] o)

  nil
  (as-resource [_] nil))

(defprotocol ResourceProperties
  (properties [_] [_ ctx] "If the semantics of the method are
  known to allow mutating the resource (i.e. the method is not 'safe'),
  the second form will be called twice, once at the start of the request
  and again after the method has been invoked to determine modified
  resource properties such as a new version (for determining the final
  ETag on the response. Any value in the map returned can be deferred,
  but if the implementation is I/O bound, implementors should attempt to
  cache the new properties during method invocation, either in
  an atom or (since methods can return the response) by assoc'ing new
  new value in a returned response."))

(extend-protocol ResourceProperties
  clojure.lang.Fn
  (properties
    ([_] {:allowed-methods #{:get}})
    ([_ ctx] {:exists? true}))

  nil
  (properties

    ([_] {:allowed-methods
          ;; We do allow :get on nil, but the response will be a 404
          #{:get}})
    ([_ ctx] {:exists? false})))


;; Allowed methods

#_(defprotocol AllowedMethods
  "Optional protocol for resources to indicate which methods are
  allowed. Other methods, such as :head and :options may be added by
  yada."
  (allowed-methods [_]
    "Return the allowed methods. Context-agnostic - can be introspected
    by tools (e.g. swagger)"))

#_(extend-protocol AllowedMethods
  clojure.lang.Fn
  (allowed-methods [_] #{:get})
  nil
  (allowed-methods [_] #{:get})
  ;; Note that yada.methods also extends this protocol later
  )

#_(defprotocol AllAllowedMethods
  "Optional protocol for resources to indicate the complete set of
  methods which are allowed."
  (all-allowed-methods [_]
    "Return the complete set of allowed methods. Context-agnostic - can
    be introspected by tools (e.g. swagger)"))

;; Representations

#_(defprotocol Representations
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

#_(extend-protocol Representations
  clojure.lang.PersistentVector (representations [v] v)
  nil (representations [_] nil))

#_(defprotocol ResourceParameters
  "Declare the resource's parameters"
  (parameters [_] "Return the parameters, by method. Must not return a deferred value."))

;; Existence

#_(defprotocol RepresentationExistence
  "Optional protocol for a resource to indicate the existence of a
  current representation. If no representation exists, this results in a
  404 response."
  (exists? [_ ctx]
    "Whether or not the resource contains a current representation. Is
    context sensitive, since the existence of a representation can often
    depend on request context, such as parameters. Therefore the context
    is given as an argument. Return truthy if a representation
    exists. Can return a deferred value."))

#_(extend-protocol RepresentationExistence
  Object (exists? [_ ctx] true)
  nil (exists? [_ ctx] false))

;; Modification

#_(defprotocol ResourceModification
  "Optional protocol for a resource to indicate when its state was last
  modified."
  (last-modified [_ ctx]
    "Return the date that the resource was last modified. Is context
    sensitive, the context is given as an argument. Can return a
    deferred value."))

#_(extend-protocol ResourceModification
  File
  (last-modified [f ctx] (Date. (.lastModified f)))
  Object
  (last-modified [_ ctx] nil)
  nil
  (last-modified [_ _] nil))

;; Versioning

#_(defprotocol ResourceVersion
  "Entity tags. Satisfying resources MUST also satisfy Representations,
  providing at least one representation (because the representation data
  is used in constructing the ETag header in the response)."
  (version [_ ctx] "Return the version of a resource. This is useful for
  conflict-detection. Can return nil, meaning no ETag."))

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
    (str (hash {:value o
                :representation rep})))
  nil
  (to-etag [o rep] nil))
