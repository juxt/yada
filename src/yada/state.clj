;; Copyright © 2015, JUXT LTD.

(ns yada.state
  (:require
   [manifold.deferred :as d])
  (import
   [clojure.core.async.impl.protocols ReadPort]
   [java.io File]
   [java.util Date]))

(defprotocol State
  (last-modified [_ ctx] "Return the date that the state was last modified.")
  (content-length [_ ctx] "Return the size of the state's represenation, if this can possibly be known up-front (return nil if this is unknown)"))

(extend-protocol State
  clojure.lang.Fn
  (last-modified [f ctx]
    (let [res (f ctx)]
      (if (d/deferrable? res)
        (d/chain res #(last-modified % ctx))
        (last-modified res ctx))))
  Number
  (last-modified [l _] (Date. l))

  File
  (last-modified [f _] (Date. (.lastModified f)))
  (content-length [f _] (.length f))

  Date
  (last-modified [d _] d)

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_ _] nil)
  (content-length [_ _] nil)

  Object
  (last-modified [_ _] nil)
  (content-length [_ _] nil)
)
