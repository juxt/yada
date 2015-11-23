;; Copyright © 2015, JUXT LTD.

(ns yada.context)

(defn exists? [ctx]
  (get (-> ctx :handler) :exists? true))
