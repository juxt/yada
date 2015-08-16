(ns yada.resources.misc
  (:require
   [yada.protocols :as p]
   [yada.methods :refer (Get Put Post Delete Options)]))

;; Each kv-arg must be off the form [method {:parameters {...} :response {...}}]

(defrecord JustMethods []
  p/ResourceAllowedMethods
  (allowed-methods [this] (keys this))

  p/ResourceModification
  (last-modified [_ ctx] (java.util.Date.))

  p/ResourceParameters
  (parameters [this]
    (reduce-kv (fn [acc k v]
                 (assoc acc k (:parameters v))) {} this))

  p/Representations
  (representations [_] [{:content-type #{"text/plain"}}])

  Get
  (GET [this ctx]
    (get-in this [:get :response]))

  Put
  (PUT [this ctx]
    (get-in this [:put :response]))

  Post
  (POST [this ctx]
    (get-in this [:post :response]))

  Delete
  (DELETE [this ctx]
    (get-in this [:delete :response]))

  Options
  (OPTIONS [this ctx]
    (get-in this [:options :response])))

(defn just-methods [& {:as args}]
  (map->JustMethods args))
