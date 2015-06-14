(ns yada.util
  (:require
   [manifold.deferred :as d])
  (:import [clojure.core.async.impl.protocols ReadPort]))

(defmacro link [ctx body]
  `(fn [~ctx] (or ~body ~ctx)))

;; Old comment :-
;; If this is something we can take from, in the core.async
;; sense, then call body again. We need this clause here
;; because: (satisfies? d/Deferrable (a/chan)) => true, so
;; (deferrable?  (a/chan) is (consequently) true too.

(defn deferrable?
  "An alternative version of deferrable that discounts
  ReadPort. Otherwise, core.async channels are considered as streams
  rather than values, which isn't what we want."
  [o]
  (and o
       (not (instance? ReadPort o))
       (d/deferrable? o)))
