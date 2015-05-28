;; Copyright © 2015, JUXT LTD.

(ns yada.state
  (:require
   [manifold.deferred :as d])
  (import
   [clojure.core.async.impl.protocols ReadPort]
   [java.io File]
   [java.util Date]))

(defprotocol State
  (exists? [_] "Whether the state actually exists")
  (last-modified [_] "Return the date that the state was last modified.")
  )

(extend-protocol State
  clojure.lang.Fn
  (last-modified [f]
    (let [res (f)]
      (if (d/deferrable? res)
        (d/chain res #(last-modified %))
        (last-modified res))))
  Number
  (last-modified [l] (Date. l))

  File
  (exists? [f] (.exists f))
  (last-modified [f] (Date. (.lastModified f)))

  Date
  (last-modified [d] d)

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_] nil)

  Object
  (last-modified [_] nil)

  )
