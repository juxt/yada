(ns yada.atom-resource
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.resource :refer (ResourceConstructor ResourceCapabilities Resource platform-charsets make-resource)]
   yada.string-resource)
  (:import [yada.string_resource StringResource]))

(defprotocol StateWrapper
  (wrap-atom [init-state a] "Given the initial value on derefencing an atom, construct a record which will manage the reference."))

(defrecord AtomicMapResource [*a wrapper *last-mod]
  Resource
  #_(produces [_ ctx] ["application/edn" "text/html;q=0.9" "application/json;q=0.9"])
  #_(produces-charsets [_ ctx] platform-charsets)
  #_(supported-methods [_ ctx] (conj (set (supported-methods wrapper ctx)) :put :post :delete))

  (exists? [_ ctx] true)

  (last-modified [_ ctx]
    (when-let [lm @*last-mod]
      lm))

  (get-state [_ _ ctx] @*a)

  (put-state! [_ content media-type ctx] (throw (ex-info "TODO" {})))

  ResourceCapabilities
  (capabilities [_] [])
  )

(defn wrap-with-watch [wrapper *a]
  (let [*last-mod (atom nil)]
    (-> *a
        ;; We add a watch to the atom so we can record when it gets
        ;; modified.
        (add-watch :last-modified
                   (fn [_ _ _ _]
                     (reset! *last-mod (to-date (now)))))
        (->AtomicMapResource wrapper *last-mod))))

(extend-protocol StateWrapper
  clojure.lang.APersistentMap
  (wrap-atom [this *a] (wrap-with-watch this *a))
  StringResource
  (wrap-atom [this *a] (wrap-with-watch this *a)))

(extend-protocol ResourceConstructor
  clojure.lang.Atom
  (make-resource [a]
    (wrap-atom (make-resource @a) a)))
