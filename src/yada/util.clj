(ns yada.util
  (:require
   [manifold.deferred :as d]))

(defmacro with-maybe [ctx body]
  `(fn [~ctx] (or ~body ~ctx)))
