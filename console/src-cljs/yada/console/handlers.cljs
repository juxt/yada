(ns yada.console.handlers
  (:require [re-frame.core :as re-frame]
            [yada.console.db :as db]))

(re-frame/register-handler
 :initialize-db
 (fn  [_ _] db/default-db))

(re-frame/register-handler
 :card-click
 (fn [db [_ card-id]]
   (assoc db :active-card {:id card-id})))

(re-frame/register-handler
 :show-all-cards
 (fn [db _]
   (dissoc db :active-card)))
