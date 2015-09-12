(ns juxt.mdl.navigation)

(defn nav [links]
  [:nav.mdl-navigation
   (for [{:keys [label href] :as link} links]
     ^{:key (or (:key (meta link)) label)}
     [:a.mdl-navigation__link {:href href} label])])
