(ns yada.resources.misc
  (:require
   [yada.resource :as res]))

(defrecord JustMethods []
  res/Resource
  (methods [this] (keys this))
  (parameters [this]
    (reduce-kv (fn [acc k v]
                 (assoc acc k (:parameters v))) {} this))
  (exists? [_ ctx] true)
  (last-modified [_ ctx] (java.util.Date.))
  (request [this method ctx]
    (when-let [f (get-in this [method :function])]
      (f ctx)))
  res/ResourceRepresentations
  (representations [_] [{:content-type #{"text/plain"}}]))

(defn just-methods [& {:as args}]
  (map->JustMethods args))
