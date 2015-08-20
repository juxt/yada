(ns yada.console.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [yada.console.handlers]
              [yada.console.subs]
              [yada.console.routes :as routes]
              [yada.console.views :as views]))

(defn mount-root []
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))

(defn ^:export reload-hook []
  (.log js/console "My figwheel reload hook!")
  (reagent/force-update-all)
  ;; TODO: Do an update of the database
  )
