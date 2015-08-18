;; Copyright © 2015, JUXT LTD.

(ns yada.resources.journal-browser
  (:require
   [bidi.bidi :refer [Matched] :as bidi]
   [hiccup.core :refer [html]]
   [yada.charset :as charset]
   [yada.journal :as journal]
   [yada.methods :refer [Get]]
   [yada.protocols :as p]
   [yada.yada :as yada]))

(defn index [idx]
  (yada/resource
   (fn [ctx]
     (html
      [:body
       [:h1 "Error index"]
       [:ul
        (for [[k v] idx]
          [:li [:a {:href (journal/path-for :entry :id k)} k]]
          )]
       ]))
   {:allowed-methods #{:get}
    :media-type "text/html"
    :charset charset/platform-charsets}))

(defn entry [e]
  (yada/resource e {:media-type "text/html"
                    :charset charset/platform-charsets}))



;; TODO: Improve rendering

(defrecord JournalBrowserResources [journal id]

  Matched
  (resolve-handler [this m]
    (when-let [r (bidi/match-route journal/routes (:remainder m))]
      (case (:handler r)
        :index {:handler (index @journal)}
        :entry (when-let [e (get-in r [:route-params :id])]
                 {:handler (entry (get @journal e))}))))

  (unresolve-handler [this m]
    nil))

(defn new-journal-browser-resources [& {:as opts}]
  (-> (map->JournalBrowserResources opts) (bidi/tag ::root)))
