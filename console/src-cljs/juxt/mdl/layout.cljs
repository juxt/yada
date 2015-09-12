(ns juxt.mdl.layout)

(defn add-keys [prefix v]
  (map (fn [a n] (vary-meta v {:key (str prefix "-" n)})) v (range)))

(defn layout [& content]
  [:div.mdl-layout.mdl-js-layout.mdl-layout--fixed-header content])

(defn header [& content]
  [:header.mdl-layout__header content])

(defn header-row [& content]
  [:div.mdl-layout__header-row content])

(defn drawer [& content]
  [:div.mdl-layout__drawer content])

(defn title [title]
  [:span.mdl-layout-title title])

(defn spacer []
  [:div.mdl-layout-spacer title])

(defn content [content]
  [:main.mdl-layout__content content])
