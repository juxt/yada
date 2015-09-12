;; Copyright © 2015, JUXT LTD.

(ns yada.context)

(defn exists? [ctx]
  (-> ctx :properties :exists?))
