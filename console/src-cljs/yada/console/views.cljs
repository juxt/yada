(ns yada.console.views
  (:require
   [re-frame.core :as re-frame]
   [yada.console.routes :refer [path-for]]))

;; --------------------
(defn home-panel []
  [:div
   [:h1 "Home"]
   [:div [:p [:a {:href (path-for :about)} "go to about Page"]]]])

(defn about-panel []
  (fn []
    [:div "This is the about page"
     [:div [:a {:href (path-for :home)} "go to home page"]]]))

;; --------------------
(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :about-panel [] [about-panel])
(defmethod panels :default [] [:div [:p "Unknown panel"]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      (panels @active-panel))))
