(ns yada.atom-resource
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.resource :refer (ResourceConstructor Resource platform-charsets)]))

(defprotocol StateWrapper
  (wrap-atom [init-state a] "Given the initial value on derefencing an atom, construct a record which will manage the reference."))

(defrecord AtomicMapResource [*a *last-mod]
  Resource
  (produces [_ ctx] ["application/edn" "text/html;q=0.9" "application/json;q=0.9"])

  (produces-charsets [_ ctx] platform-charsets)

  (exists? [_ ctx] true)

  (last-modified [_ ctx]
    (when-let [lm @*last-mod]
      lm))

  (get-state [_ _ ctx] @*a))

(extend-protocol StateWrapper
  clojure.lang.APersistentMap
  (wrap-atom [_ *a]
    (let [*last-mod (atom nil)]
      (-> *a
          ;; We add a watch to the atom so we can record when it gets
          ;; modified.
          (add-watch :last-modified
                     (fn [_ _ _ _]
                       (reset! *last-mod (to-date (now)))))
          (->AtomicMapResource *last-mod)))))

(extend-protocol ResourceConstructor
  clojure.lang.Atom
  (make-resource [a]
    (wrap-atom @a a)))
