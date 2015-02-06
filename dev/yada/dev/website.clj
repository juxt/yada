(ns yada.dev.website
  (:require
   [schema.core :as s]
   [bidi.bidi :refer (path-for RouteProvider handler)]
   [hiccup.core :refer (html)]
   [com.stuartsierra.component :refer (using)]
   [tangrammer.component.co-dependency :refer (co-using)]))

(defn index [router swagger-ui-resources pets-api]
  (fn [req]
    {:status 200
     :body (html
            [:h1 "Welcome to yada"]
            [:p "This is a simple console to help you understand what
            yada is and how it can help you write web apps and APIs."]

            [:ol
             [:li [:a {:href (path-for (:routes @router) :yada.dev.demo/index)} "Documentation"]]
             [:li [:a {:href
                       (format "%s/index.html?url=%s/swagger.json"
                               (path-for (:routes @router) swagger-ui-resources)
                               (path-for (:routes @router) pets-api)
                               )}
                   "Swagger UI"
                   ] " - to demonstrate Swagger wrapper"]
             ])}))

(defrecord Website [router swagger-ui pets-api]
  RouteProvider
  (routes [this]
    ["/index.html" (handler ::index
                            (index router (:target swagger-ui) (:api pets-api)))]))

(defn new-website [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->Website)
      (using [:swagger-ui :pets-api])
      (co-using [:router])))
