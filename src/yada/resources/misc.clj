(ns yada.resources.misc
  (:require
   [yada.resource :as res]))

;; Each kv-arg must be off the form [method {:parameters {...} :response {...}}]

(defrecord JustMethods []
  res/Resource
  (methods [this] (keys this))
  (parameters [this]
    (reduce-kv (fn [acc k v]
                 (assoc acc k (:parameters v))) {} this))
  (exists? [_ ctx] true)
  (last-modified [_ ctx] (java.util.Date.))
  (request [this method ctx]
    (get-in this [method :response]))
  res/ResourceRepresentations
  (representations [_] [{:content-type #{"text/plain"}}]))

(defn just-methods [& {:as args}]
  (map->JustMethods args))
