(ns yada.util
  (:require
   [manifold.deferred :as d]))

(defmacro link [ctx body]
  `(fn [~ctx] (or ~body ~ctx)))
