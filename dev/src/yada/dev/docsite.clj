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
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   yada.bidi
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   yada.resources.file-resource
   [yada.yada :as yada :refer [yada]])
  (:import [modular.bidi Router]))

(def titles
  {2046 "Multipurpose Internet Mail Extensions (MIME) Part Two: Media Types"
   2616 "Hypertext Transfer Protocol -- HTTP/1.1"
   7230 "Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing"
   7231 "Hypertext Transfer Protocol (HTTP/1.1): Semantics and Content"
   7232 "Hypertext Transfer Protocol (HTTP/1.1): Conditional Requests"
   7233 "Hypertext Transfer Protocol (HTTP/1.1): Range Requests"
   7234 "Hypertext Transfer Protocol (HTTP/1.1): Caching"
   7235 "Hypertext Transfer Protocol (HTTP/1.1): Authentication"
   7236 "Initial Hypertext Transfer Protocol (HTTP)\nAuthentication Scheme Registrations"
   7237 "Initial Hypertext Transfer Protocol (HTTP) Method Registrations"
   7238 "The Hypertext Transfer Protocol Status Code 308 (Permanent Redirect)"
   7239 "Forwarded HTTP Extension"
   7240 "Prefer Header for HTTP"
   7578 "Returning Values from Forms: multipart/form-data"
   })

(defn rfc []
  (fn [req]
    (let [source (io/resource (format "spec/rfc%s.html" (get-in req [:route-params :rfc])))]
      (infof "source is %s" source)
      ((yada source) req))))

(defn index [{:keys [*router *cors-demo-router *talks-router config]}]
  (yada
   (new-template-resource
    "templates/page.html"
    ;; We delay the model until after the component's start phase
    ;; because we need the *router.
    (delay {:content
            (html
             [:div.container
              [:h2 "Welcome to " [:span.yada "yada"] "!"]
              [:ol
               [:li [:a {:href (path-for @*router :yada.dev.user-manual/user-manual)} "The " [:span.yada "yada"] " manual"]
                " - the single authority on all things " [:span.yada "yada"]]

               [:li [:a {:href
                         (format "%s/index.html?url=%s/swagger.json"
                                 (path-for @*router :swagger-ui)
                                 (path-for @*router :yada.dev.user-api/user-api)
                                 )}
                     "Swagger UI"]
                " - to demonstrate Swagger integration"]
               [:li [:a {:href (str
                                (config/cors-demo-origin config)
                                (path-for @*cors-demo-router :yada.dev.cors-demo/index))} "CORS demo"] " - to demonstrate CORS support"]
               [:li [:a {:href (str
                                (config/talks-origin config)
                                (path-for @*talks-router :yada.dev.talks/index))} "Talks"]]
               [:li [:a {:href (str
                                (path-for @*router :yada.dev.phonebook/phonebook))} "Phonebook demo"]]

               [:li "Relevant RFCs"
                [:ul
                 (for [i (sort (keys titles))]
                   [:li [:a {:href (format "/spec/rfc%d" i)}
                         (format "RFC %d: %s" i (or (get titles i) ""))]])]]]
              ])}))

   {:id ::index}))

(s/defrecord Docsite [*router :- (co-dep Router)
                      *cors-demo-router :- (co-dep Router)
                      *talks-router :- (co-dep Router)
                      config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
    ["/"
     [["index.html" (index component)]
      [["spec/rfc" :rfc] (rfc)]
      ]]))

(defn new-docsite [& {:as opts}]
  (-> (map->Docsite opts)
      (co-using [:router :cors-demo-router :talks-router])))
