(ns yada.console.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 :devices
 (fn [db]
   (reaction (:devices @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))

(re-frame/register-sub
 :active-device
 (fn [db _]
   (reaction (:active-device @db))))
