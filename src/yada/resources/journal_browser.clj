;; Copyright © 2015, JUXT LTD.

(ns yada.resources.journal-browser
  (:require
   [bidi.bidi :refer [Matched] :as bidi]
   [hiccup.core :refer [html]]
   [yada.charset :as charset]
   [yada.journal :as journal]
   [yada.protocols :as p]
   [yada.resource :refer [resource]]
   [yada.yada :as yada :refer [yada]]))

(def access-control
  {:allow-origin "*"
   :expose-headers ["Server" "Date" "Content-Length" "Access-Control-Allow-Origin"]})

(defn index [idx]
  (yada
   (resource
    {:produces [{:media-type #{"text/html" "application/edn" "application/edn;pretty=true"}
                 :charset charset/platform-charsets}]
     :access-control access-control
     :methods {:get (fn [ctx]
     (case (-> ctx :response :produces :media-type :name)
       "text/html"
       (html
        [:body
         [:h1 "Error index"]
         [:ul
          (for [[k v] idx]
            [:li [:a {:href (journal/path-for :entry :id k)} k]])]])
       "application/edn"
       (into {} (for [[k v] idx]
                  [k (:chain (get idx k))]))))}})))

(defn entry [e]
  (yada e {:media-type "text/html"
           :charset charset/platform-charsets
           :access-control access-control}))

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
