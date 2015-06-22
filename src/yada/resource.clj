;; Copyright © 2015, JUXT LTD.

(ns yada.resource
  (:require [clojure.tools.logging :refer :all]
            [manifold.deferred :as d]
            [yada.charset :refer (to-charset-map)]
            [yada.util :refer (deferrable?)])
  (:import [clojure.core.async.impl.protocols ReadPort]
           [java.io File InputStream]
           [java.util Date]))

(defprotocol ResourceFetch
  (fetch [this ctx] "Fetch the resource, such that questions can be answered about it. Anything you return from this function will be available in the :resource entry of ctx and will form the type that will be used to dispatch other functions in this protocol. You can return a deferred if necessary (indeed, you should do so if you have to perform some IO in this function). Often, you will return 'this', perhaps augmented with some additional state. Sometimes you will return something else."))

(defprotocol Resource
  "A protocol for describing a resource: where it is, when it was last
  updated, how to change it, etc. A resource may hold its state, or be able to educe the state on demand (via get-state)."

  (exists? [_ ctx] "Whether the resource actually exists")

  (last-modified [_ ctx] "Return the date that the resource was last modified.")

  (produces [_] [_ ctx]
    "Return the mime types that can be produced from this resource. The first form is request-context independent, suitable for up-front consumption by introspectng tools such as swagger. The second form can be more sensitive to the request context. Return a string or strings, such as text/html. If text, and multiple charsets are possible, return charset information. e.g. [\"text/html;charset=utf8\" \"text/html;charset=unicode-1-1\"]")
  (produces-charsets [_ ctx] "Return the charsets that can be produced from this resource.")

  (content-length [_ ctx] "Return the content length, if possible.")

  (get-state [_ content-type ctx] "Return the state, formatted to a representation of the given content-type and charset. Returning nil results in a 404. Get the charset from the context [:request :charset], if you can support different charsets. A nil charset at [:request :charset] means the user-agent can support all charsets, so just pick one.")

  (put-state! [_ content content-type ctx] "Overwrite the state with the data. To avoid inefficiency in abstraction, satisfying types are required to manage the parsing of the representation in the request body. If a deferred is returned, the HTTP response status is set to 202")

  (post-state! [_ ctx] "Insert a new sub-resource. See write! for semantics.")

  (delete-state! [_ ctx] "Delete the state. If a deferred is returned, the HTTP response status is set to 202"))

(def supported-charsets
  (concat
   [(to-charset-map (.name (java.nio.charset.Charset/defaultCharset)))]
   (map #(assoc % :weight 0.9) (map to-charset-map (keys (java.nio.charset.Charset/availableCharsets))))))

(extend-protocol ResourceFetch
  clojure.lang.Fn
  (fetch [f ctx]
    (let [res (f ctx)]
      (if (deferrable? res)
        (d/chain res #(fetch % ctx))
        (fetch res ctx))))
  nil ; The user has not elected to specify a resource, that's fine (and common)
  (fetch [_ ctx] nil)
  Object
  (fetch [o ctx] o))

(extend-protocol Resource

  String
  (fetch [s ctx] s)
  (exists? [s ctx] true)
  (last-modified [s _] nil)
  (get-state [s content-type ctx] s)
  ;; Without attempting to actually parse it (which isn't completely
  ;; impossible) we're not able to guess the content-type of this
  ;; string, so we return nil.
  (produces [s] nil)
  (produces [s ctx] nil)
  (produces-charsets [_ ctx] supported-charsets)

  (content-length [_ _] nil)

  #_Date
  #_(last-modified [d _] d)

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_ _] nil)
  (get-state [_ content-type ctx] nil)
  (content-length [_ _] nil)
  (produces [_] nil)
  (produces [_ _] nil)
  (produces-charsets [_ _] nil)

  #_Object
  #_(last-modified [_ _] nil)
  #_(content-length [_ _] nil)
  #_(produces-charsets [_ _] nil)

  )


(defprotocol ResourceConstructor
  (make-resource [_] "Make a resource. Often, resources need to be constructed rather than simply extending types with the Resource protocol. For example, we sometimes need to know the exact time that a resource is constructed, to support time-based conditional requests. For example, a simple StringResource is immutable, so by knowing the time of construction, we can precisely state its Last-Modified-Date."))

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
