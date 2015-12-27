(ns yada.console.views
  (:require
   [cljs.pprint :as pp]
   [clojure.string :as str]
   [juxt.mdl.layout :as lo]
   [juxt.mdl.navigation :as nav]
   [juxt.mdl.tables :as t]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [yada.console.routes :refer [path-for set-token!]]))

(enable-console-print!)

;; --------------------
(defn header []
  [lo/header
   ^{:key "header-row1"}
   [lo/header-row
    ^{:key "title"} [lo/title "yada console"]
    ^{:key "spacer"} [lo/spacer]
    ^{:key "nav"} [nav/nav
                   [{:label "Documentation" :href "https://yada.juxt.pro" :key "doc"}
                    {:label "Github" :href "https://github.com/juxt/yada" :key "gh"}
                    {:label "JUXT" :href "https://juxt.pro" :key "jx"}]]]])

(defn grid [& content]
  [:div.mdl-grid {:style {:position "relative"}} content])

(defn cell [width content]
  (fn []
    (let [elk (keyword (str "div.mdl-cell.mdl-cell--" width "-col"))]
      [elk content])))

(defn card [id]
  (let [card (re-frame/subscribe [:cards id])
        active-card (re-frame/subscribe [:active-card])]
    (fn []
      (let [active (= id (:id @active-card))]
        [:div.demo-card-wide.mdl-card.mdl-shadow--2dp
         (when active {:class "active-card"})
         [:div.mdl-card__title
          {:on-click (fn [ev] (set-token! (path-for :card :card-id id)))
           :style
           (merge {}
                  (let [background (:background @card)]
                    (if background
                      {:background (cond (string? background) (str "url('" background "') center / cover;")
                                         (keyword? background) (-name background))}
                      {:background "#002"})))}

          [:h2.mdl-card__title-text (:title @card)]]
         [:div.mdl-card__supporting-text (:supporting-text @card)]

         [:div.mdl-card__actions.mdl-card--border
          (if (not active)
            (let [a "Show" #_(:actions @card)]
              [:a.mdl-button.mdl-button--colored.mdl-js-button.mdl-js-ripple-effect
               {:href (path-for :card :card-id id)}
               a]))]

         [:div.mdl-card__menu
          (if active
            [:button.mdl-button.mdl-button--icon.mdl-js-button.mdl-js-ripple-effect
             {:on-click (fn [ev] (set-token! (path-for :cards)))}
             [:i.material-icons "close"]]
            )]]))))

(defn cards [cards]
  [grid
   (for [card-id (keys cards)]
     ^{:key (str "cell-" card-id)}
     [cell 4 [card card-id]])])

;; --------------------

(defn main-panel []
  (let [db (re-frame/subscribe [:db])]
    (fn []
      [lo/layout
       ^{:key "header"} [header]

       ^{:key "content"}
       [lo/content
        [:div.page-content
         [cards (:cards @db)]

         [:div
          [:h4 "Database"]
          (when @db
            [:pre (with-out-str (pp/pprint @db))])]]]])))
