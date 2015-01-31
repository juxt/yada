(ns yada.dev.website
  (:require
   [schema.core :as s]
   [modular.bidi :refer (WebService)]
   [bidi.bidi :refer (path-for)]
   [hiccup.core :refer (html)]
   [com.stuartsierra.component :refer (using)]
   [tangrammer.component.co-dependency :refer (co-using)]))

(defn index [router swagger-ui-resources api]
  (fn [req]
    {:status 200
     :body (html
            [:h1 "Welcome to yada"]
            [:p "This is a simple console to help you understand what
            yada is and how it can help you write web apps and APIs."]
            [:ol
             [:li [:a {:href
                       (format "%s/index.html?url=%s/swagger.json"
                               (path-for (:routes @router) swagger-ui-resources)
                               (path-for (:routes @router) api)
                               )}
                   "Swagger UI"
                   ] " - for the example web API"]
             [:li [:a {:href "#"} "Documentation"] " - coming soon"]])}))

(defrecord Website [router swagger-ui api]
  WebService
  (request-handlers [this] {::index (index router (:target swagger-ui) (:api api))})
  (routes [this] ["index.html" ::index])
  (uri-context [_] "/"))

(defn new-website [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->Website)
      (using [:swagger-ui :api])
      (co-using [:router])))
