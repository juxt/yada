;; Copyright © 2015, JUXT LTD.

(ns yada.context)

;; TODO: Can this go elsewhere? Or inlined?

;; TODO: On the flip-side, sometimes you need code to enforce the
;; data's semantics.

(defn exists?
  "We assume every resource exists unless it says otherwise, with an
  explicit exists? entry in its properties."
  [ctx]
  (let [props (:properties ctx)]
    (if (contains? props :exists?)
      (:exists? props)
      true)))
