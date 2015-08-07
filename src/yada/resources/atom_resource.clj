;; Copyright © 2015, JUXT LTD.

(ns yada.resources.atom-resource
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.resource :refer (ResourceCoercion ResourceAllowedMethods ResourceModification ResourceRepresentations platform-charsets make-resource ResourceParameters parameters allowed-methods)]
   [yada.methods :refer (Get Put)]
   [schema.core :as s]
   yada.resources.string-resource)
  (:import [yada.resources.string_resource StringResource]))

(defprotocol StateWrapper
  (wrap-atom [init-state a] "Given the initial value on derefencing an atom, construct a record which will manage the reference."))

(defrecord AtomicMapResource [*a wrapper *last-mod]
  ResourceAllowedMethods
  (allowed-methods [_] (conj (set (allowed-methods wrapper)) :put :post :delete))

  ResourceModification
  (last-modified [_ ctx]
    (when-let [lm @*last-mod] lm))

  ResourceParameters
  (parameters [_] (merge
                   (when (satisfies? ResourceParameters wrapper)
                     (parameters wrapper))
                   {:put {:body s/Str}})) ;; TODO: Not just a string, depends on wrapper

  ResourceRepresentations
  (representations [_] [{:method #{:get :head}
                         :content-type #{"application/edn" "text/html;q=0.9" "application/json;q=0.9"}
                         :charset platform-charsets}])

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
        (->AtomicMapResource wrapper *last-mod))))

(extend-protocol StateWrapper
  clojure.lang.APersistentMap
  (wrap-atom [this *a] (wrap-with-watch this *a))
  StringResource
  (wrap-atom [this *a] (wrap-with-watch this *a)))

(extend-protocol ResourceCoercion
  clojure.lang.Atom
  (make-resource [a]
    (wrap-atom (make-resource @a) a)))
