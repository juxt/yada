(ns yada.console.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :db
 (fn [db _]
   (reaction @db)))

(re-frame/register-sub
 :cards
 (fn [db [_ id]]
   (reaction (get-in @db [:cards id]))))

(re-frame/register-sub
 :active-card
 (fn [db _]
   (reaction (:active-card @db))))
