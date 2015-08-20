(ns yada.console.handlers
    (:require [re-frame.core :as re-frame]
              [yada.console.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/register-handler
 :set-active-panel
 (fn [db [_ active-panel id]]
   (case active-panel
     (:home-panel :about-panel)
     (assoc db :active-panel active-panel)
     :device-panel
     (->
      db
      (assoc :active-panel active-panel)
      (assoc :active-device (get (:devices db) id))))))

(re-frame/register-handler
 :add-device
 (fn [db [_]]
   (update-in db [:devices] conj ["7" {:name "New device"}])))
