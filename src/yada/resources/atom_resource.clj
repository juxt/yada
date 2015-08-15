;; Copyright © 2015, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.methods :refer (Get Put)]
   [schema.core :as s]
   yada.resources.string-resource)
  (:import [yada.resources.string_resource StringResource]))

(defprotocol StateWrapper
  (wrap-atom [init-state a] "Given the initial value on derefencing an atom, construct a record which will manage the reference."))

(defrecord AtomResource [*a wrapper *last-mod]
  p/ResourceAllowedMethods
  (allowed-methods [_] (conj (set (p/allowed-methods wrapper)) :put :post :delete))

  p/ResourceModification
  (last-modified [_ ctx]
    (when-let [lm @*last-mod] lm))

  p/ResourceParameters
  (parameters [_] (merge
                   (when (satisfies? p/ResourceParameters wrapper)
                     (p/parameters wrapper))
                   {:put {:body s/Str}})) ;; TODO: Not just a string, depends on wrapper

  p/ResourceRepresentations
  (representations [_]
    (when (satisfies? p/ResourceRepresentations wrapper)
      (p/representations wrapper)))

  p/ResourceVersion
  (version [_ ctx]
    (when (satisfies? p/ResourceVersion wrapper)
      (p/version wrapper ctx)))

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
