;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test utilities"}
    yada.test-util
  (:require
   [manifold.stream :as s]
   [yada.request-body :refer [process-request-body]]
   [byte-streams :as bs]
   [clojure.test :refer [is]]))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"[0-9a-f]+" etag)))

(defn to-string [s]
  (bs/convert s String))

(defmacro is-coercing-correctly?
  [expected value content-type]
  `(let [expected# ~expected
         res# (process-request-body {} ~value ~content-type)]
     (is (= {:body expected#}
            res#))))
