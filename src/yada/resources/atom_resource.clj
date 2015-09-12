;; Copyright © 2015, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer (Get Put) :as methods]
   [schema.core :as s]
   yada.resources.string-resource)
  (:import [yada.resources.string_resource StringResource]))

(defprotocol StateWrapper
  (wrap-atom [init-state a] "Given the initial value on derefencing an atom, construct a record which will manage the reference."))

(defrecord AtomResource [*a wrapper *last-mod]
  p/ResourceProperties
  (properties [_]
    (let [props (p/properties wrapper)]
      (merge props
             {:allowed-methods (conj (set (or
                                           (:allowed-methods props)
                                           (methods/infer-methods wrapper)))
                                     :put :post :delete)})))
  (properties [_ ctx]
    (merge
     ;; TODO: Make sure that in the case of the string we are getting
     ;; the version from the current string, not the original string
     (p/properties wrapper ctx)
     (when-let [lm @*last-mod]
       {:last-modified lm})))

  Get
  (GET [_ ctx] @*a)

  Put
  (PUT [_ ctx] (reset! *a (get-in ctx [:parameters :body]))))

(defn wrap-with-watch [wrapper *a]
  (let [*last-mod (atom nil)]
    (-> *a
        ;; We add a watch to the atom so we can record when it gets
        ;; modified.
        (add-watch :last-modified
                   (fn [_ _ _ _]
                     (reset! *last-mod (to-date (now)))))
        (->AtomResource wrapper *last-mod))))

(extend-protocol StateWrapper
  clojure.lang.APersistentMap
  (wrap-atom [this *a] (wrap-with-watch this *a))
  StringResource
  (wrap-atom [this *a] (wrap-with-watch this *a)))

(extend-protocol p/ResourceCoercion
  clojure.lang.Atom
  (as-resource [a]
    (wrap-atom (p/as-resource @a) a)))
