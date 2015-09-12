(ns juxt.mdl.tables)

(defn row [& cells]
  [:tr
   (for [cell cells]
     [:td cell])])

(defn table [{:keys [cols]} rows]
  [:table.mdl-data-table.mdl-data-table--selectable.mdl-shadow--2dp
   (when cols
     [:thead
      [:tr
       (for [col cols]
         [:th col])]])
   [:tbody
    (for [row rows
          :let [id (first row)]]
      [:tr
       (for [[col cell] (map vector cols row)]
         ^{:key (str id "-" col)} [:td cell])])]])
