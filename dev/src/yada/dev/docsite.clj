;; Copyright © 2015, JUXT LTD.

(ns yada.dev.docsite
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [bidi.bidi :refer (RouteProvider tag)]
   [com.stuartsierra.component :refer (using)]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.template :as template :refer (render-template)]
   [schema.core :as s]
   yada.bidi
   [yada.dev.config :as config]
   yada.resources.file-resource
   [yada.yada :as yada :refer [yada]]))

(def titles
  {7230 "Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing"
   7231 "Hypertext Transfer Protocol (HTTP/1.1): Semantics and Content"
   7232 "Hypertext Transfer Protocol (HTTP/1.1): Conditional Requests"
   7233 "Hypertext Transfer Protocol (HTTP/1.1): Range Requests"
   7234 "Hypertext Transfer Protocol (HTTP/1.1): Caching"
   7235 "Hypertext Transfer Protocol (HTTP/1.1): Authentication"
   7236 "Initial Hypertext Transfer Protocol (HTTP)\nAuthentication Scheme Registrations"
   7237 "Initial Hypertext Transfer Protocol (HTTP) Method Registrations"
   7238 "The Hypertext Transfer Protocol Status Code 308 (Permanent Redirect)"
   7239 "Forwarded HTTP Extension"
   7240 "Prefer Header for HTTP"})

(defn rfc []
  (fn [req]
    (let [source (io/resource (format "spec/rfc%s.html" (get-in req [:route-params :rfc])))]
      (infof "source is %s" source)
      ((yada source) req))))

(defn index [{:keys [templater *router *cors-demo-router config]}]
  (yada
   (fn [ctx]
     ;; TODO: Replace with template resource
     (render-template
      templater
      "templates/page.html.mustache"
      {:content
       (html
        [:div.container
         [:h2 "Welcome to " [:span.yada "yada"] "!"]
         [:ol
          [:li [:a {:href (path-for @*router :yada.dev.user-manual/user-manual)} "User manual"]]
          [:li "HTTP and related specifications"
           [:ul
            [:li [:a {:href "/spec/rfc2616"} "RFC 2616: Hypertext Transfer Protocol -- HTTP/1.1"]]
            (for [i (range 7230 (inc 7240))]
              [:li [:a {:href (format "/spec/rfc%d" i)}
                    (format "RFC %d: %s" i (or (get titles i) ""))]])]]
          [:li [:a {:href
                    (format "%s/index.html?url=%s/swagger.json"
                            (path-for @*router :swagger-ui)
                            (path-for @*router :yada.dev.user-api/user-api)
                            )}
                "Swagger UI"]
           " - to demonstrate Swagger integration"]
          [:li [:a {:href (format "%s%s"
                                  (config/cors-prefix config)
                                  (path-for @*cors-demo-router :yada.dev.cors-demo/index))} "CORS demo"] " - to demonstrate CORS support"]]
         ])}))
   {:id ::index
    :representations [{:media-type #{"text/html"}
                       :charset #{"utf-8"}
                       }]}))

(defrecord Docsite []
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      [["spec/rfc" :rfc] (rfc)]
      #_["dir/" (-> (yada.bidi/resource-branch (io/file "dev/resources"))
                  (tag ::dir))]]]))

(defn new-docsite [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {:config s/Any})
           map->Docsite)
      (using [:templater])
      (co-using [:router :cors-demo-router])))
