(ns yada.resources.misc
  (:require
   [yada.resource :as res]
   [yada.methods :refer (Get Put Post Delete Options)]))

;; Each kv-arg must be off the form [method {:parameters {...} :response {...}}]

(defrecord JustMethods []
  res/Resource
  (methods [this] (keys this))
  (exists? [_ ctx] true)
  (last-modified [_ ctx] (java.util.Date.))

  res/ResourceParameters
  (parameters [this]
    (reduce-kv (fn [acc k v]
                 (assoc acc k (:parameters v))) {} this))

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
    (get-in this [:options :response]))

  res/ResourceRepresentations
  (representations [_] [{:content-type #{"text/plain"}}]))

(defn just-methods [& {:as args}]
  (map->JustMethods args))
