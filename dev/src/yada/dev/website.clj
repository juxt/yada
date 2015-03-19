(ns yada.dev.website
  (:require
   [schema.core :as s]
   [bidi.bidi :refer (RouteProvider tag)]
   [modular.bidi :refer (path-for)]
   [clojure.java.io :as io]
   [hiccup.core :refer (html)]
   [com.stuartsierra.component :refer (using)]
   [modular.template :as template :refer (render-template)]
   [modular.component.co-dependency :refer (co-using)]
   [yada.yada :refer (yada)]))

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

(defn index [{:keys [*router templater]} pets-api]
  (yada
   :body
   {"text/html"
    (fn [ctx]
      (render-template
       templater
       "templates/page.html.mustache"
       {:content
        (let [header [:button.btn.btn-primary {:onClick "testAll()"} "Repeat tests"]]
          (html
           [:div.container
            [:h1 "Welcome to " [:span.yada "yada"] "!"]
            [:ol
             [:li [:a {:href (path-for @*router :yada.dev.user-guide/user-guide)} "User guide"] " (and " [:a {:href (path-for @*router :yada.dev.user-guide/tests)} "tests"] ")"]
             [:li "HTTP and related specifications"
              [:ul
               [:li [:a {:href "/static/spec/rfc2616.html"} "RFC 2616: Hypertext Transfer Protocol -- HTTP/1.1"]]
               (for [i (range 7230 (inc 7240))]
                 [:li [:a {:href (format "/static/spec/rfc%d.html" i)}
                       (format "RFC %d: %s" i (or (get titles i) ""))]])]]
             [:li [:a {:href
                       (format "%s/index.html?url=%s/swagger.json"
                               (path-for @*router :swagger-ui)
                               (path-for @*router pets-api)
                               )}
                   "Swagger UI"]
              " - to demonstrate Swagger integration"]]]))}))}))

(defrecord Website [*router templater pets-api]
  RouteProvider
  (routes [component]
    ["/index.html" (-> (index component (:api pets-api))
                       (tag ::index))]))

(defn new-website [& {:as opts}]
  (-> (->> opts
           (merge {})
           (s/validate {})
           map->Website)
      (using [:templater :pets-api])
      (co-using [:router])))
