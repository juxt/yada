(ns yada.resources.misc
  (:require
   [clj-time.core :refer [now]]
   [clj-time.coerce :refer [to-date]]
   [yada.protocols :as p]
   [yada.methods :refer [Get Put Post Delete Options]]))

(defrecord JustMethods []
  p/ResourceProperties
  (resource-properties [this]
    {:allowed-methods (keys this)
     :parameters (reduce-kv (fn [acc k v]
                              (cond-> acc (:parameters v)
                                      (assoc k (:parameters v))))
                            {} this)
     :representations [{:media-type #{"text/plain"}}]})
  (resource-properties [_ ctx]
    {:last-modified (to-date (now))})

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
