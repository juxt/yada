(ns yada.console.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [yada.console.handlers]
              [yada.console.subs]
              [yada.console.routes :as routes]
              [yada.console.views :as views]
              [yada.xhr :as xhr :refer (GET)]))

(enable-console-print!)

(defn ^:export init []
  (re-frame/dispatch-sync [:initialize-db])
  (routes/init)
  (reagent/render [views/main-panel] (.getElementById js/document "app")))

(defn ^:export reload-hook []
  (println "Reload!")
  (reagent/force-update-all))
