;; Copyright © 2015, JUXT LTD.

(ns phonebook.html
  (:require
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [yada.yada :as yada]))

(defn index-html [ctx entries q]
  (html
   [:body
    [:form#search {:method :get}
     [:input {:type :text :name :q :value q}]
     [:input {:type :submit :value "Search"}]]

    (if (not-empty entries)
      [:table
       [:thead
        [:tr
         [:th "Entry"]
         [:th "Surname"]
         [:th "Firstname"]
         [:th "Phone"]]]

       [:tbody
        (for [[id {:keys [surname firstname phone]}] entries
              :let [href (yada/href-for ctx :phonebook.api/entry {:route-params {:entry id}})]]
          [:tr
           [:td [:a {:href href} href]]
           [:td surname]
           [:td firstname]
           [:td phone]])]]
      [:h2 "No entries"])

    [:h4 "Add entry"]

    [:form {:method :post}
     [:style "label { margin: 6pt }"]
     [:p
      [:label "Surname"]
      [:input {:name "surname" :type :text}]]
     [:p
      [:label "Firstname"]
      [:input {:name "firstname" :type :text}]]
     [:p
      [:label "Phone"]
      [:input {:name "phone" :type :text}]]
     [:p
      [:input {:type :submit :value "Add entry"}]]]]))

(defn entry-html [{:keys [firstname surname phone]}
                  {:keys [entry index]}]
  (html
   [:body
    [:script (format "index=\"%s\";" index)]
    [:script (format "entry=\"%s\";" entry)]
    [:h2 (format "%s %s" firstname surname)]
    [:p "Phone: " phone]

    [:h4 "Update entry"]
    [:form#entry
     [:style "label { margin: 6pt }"]
     [:p
      [:label "Firstname"]
      [:input {:type :text :name "firstname" :value firstname}]]
     [:p
      [:label "Surname"]
      [:input {:type :text :name "surname" :value surname}]]
     [:p
      [:label "Phone"]
      [:input {:type :text :name "phone" :value phone}]]]

    [:button {:onclick (format "phonebook.update('%s')" entry)} "Update"]
    [:button {:onclick (format "phonebook.delete('%s')" entry)} "Delete"]
    [:p [:a {:href index} "Index"]]
    (when-let [js (io/resource "js/phonebook.js")]
      [:script (slurp js)])]))
