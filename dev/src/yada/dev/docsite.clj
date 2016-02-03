;; Copyright © 2015, JUXT LTD.

(ns yada.dev.docsite
  (:require
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [bidi.bidi :refer [RouteProvider tag]]
   [com.stuartsierra.component :refer (using)]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [schema.core :as s]
   [yada.dev.async :as async]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   yada.resources.file-resource
   [yada.resources.classpath-resource :refer [new-classpath-resource]]
   [yada.dev.hello :as hello]
   [yada.swagger :as swagger :refer [swaggered]]
   [yada.yada :as yada :refer [yada resource]])
  (:import [modular.bidi Router]
           [com.stuartsierra.component SystemMap]
           [modular.component.co_dependency CoDependencySystemMap]))

(def titles
  {2046 "Multipurpose Internet Mail Extensions (MIME) Part Two: Media Types"
   2183 "Communicating Presentation Information in Internet Messages: The Content-Disposition Header Field"
   2616 "Hypertext Transfer Protocol -- HTTP/1.1"
   5322 "Internet Message Format"
   6265 "HTTP State Management Mechanism"
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
      ((yada source) req))))

(defn index [{:keys [*router phonebook config]}]
  (yada
   (->
    (new-template-resource
     "templates/page.html"
     ;; We delay the model until after the component's start phase
     ;; because we need the *router.
     (delay {:banner true
             :content
             (html
              [:div.container
               [:h2 "Welcome to " [:span.yada "yada"] "!"]
               [:ol
                [:li [:a {:href (path-for @*router :yada.dev.user-manual/user-manual)} "The " [:span.yada "yada"] " manual"]
                 " — the single authority on all things " [:span.yada "yada"]]

                [:li "Examples — self-contained apps for you to explore"
                 [:ul
                  [:li [:a {:href (path-for @*router :yada.dev.hello/index)} "Hello World!"] " — to introduce " [:span.yada "yada"] " in the proper way"]
                  
                  [:li
                   [:a {:href (str
                               (config/origin config :phonebook)
                               (path-for (:server phonebook) :phonebook.api/index))} "Phonebook"]
                   [:a {:href
                        ;; TODO: use bidi's path-for
                        (format "%s/phonebook-swagger.html?url=%s"
                                (path-for @*router :swagger-ui)
                                (str (config/origin config :docsite) (path-for @*router ::phonebook-swagger-spec))
                                
                                )}
                    " (Swagger)"]
                   " — to demonstrate custom records implementing standard HTTP methods"]

                  [:li [:a {:href (path-for @*router :yada.dev.security/index)} "Security"] " — to demonstrate authentication and authorization features"]

                  [:li [:a {:href (path-for @*router :yada.dev.async/sse-demo)} "SSE demo"] " — to demonstrate Server Sent Events"]

                  ]]

                #_[:li [:a {:href (path-for @*router :yada.dev.console/index :path "")}
                      "The " [:span.yada "yada"] " console"] " — to capture traffic and debug your API (work in progress)"]

                [:li [:a {:href (path-for @*router :yada.dev.talks/index)} "Talks"]]

                [:li "Relevant standards"
                 [:ul
                  (for [i (sort (keys titles))]
                    [:li [:a {:href (format "/spec/rfc%d" i)}
                          (format "RFC %d: %s" i (or (get titles i) ""))]])]]

                ]
               ])}))
    (assoc :id ::index))))

(s/defrecord Docsite [*router :- (co-dep Router)
                      phonebook :- SystemMap
                      config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
    [""
     [["/index.html" (index component)]

      ["/hello.html" (hello/index *router)]

      ["/body.html" (yada
                     (resource
                      {:produces "application/json"
                       :methods
                       {:get
                        {:response (fn [_] {:greeting "Hello"})}}}))]

      ["/dir/" (yada (io/file "talks"))]
      ["/jar" (yada (new-classpath-resource "META-INF/resources/webjars/swagger-ui/2.1.3"))]

      ["/h1.html" (yada [:h1 "Heading"])]

      ["/404" (resource
               {:properties {:exists? false}
                :methods {:get nil}
                :responses {404 {:description "Not found"
                                 :produces #{"text/html" "text/plain;q=0.9"}
                                 :response (let [msg "Oh dear I couldn't find that"]
                                             (fn [ctx]
                                               (case (get-in ctx [:response :produces :media-type :name])
                                                 "text/html" (html [:h2 msg])
                                                 (str msg \newline))))}}})]

      ["/406" (resource
               {:methods {:get {:response (fn [ctx] (throw (ex-info "" {:status 400})))
                                :produces #{"text/html"}}}
                :responses {#{406} {:description "Redirect on 406"
                                    :produces #{"text/html" "text/plain;q=0.9"}
                                    :response (fn [ctx]
                                                (-> (:response ctx) 
                                                    (assoc :status 304)
                                                    (update :headers conj ["Location" "/foo"])))}}})]

      ["/api" 
       (swaggered
        {:info {:title "Hello World!"
                :version "1.0"
                :description "A greetings service"}
         :basePath "/api"}
        ["/greetings"
         [
          ["/hello" (yada "Hello World!\n")]
          ["/goodbye" (yada "Goodbye!\n")]
          ]
         ])]

      ["/phonebook-api/swagger.json"
       (-> (yada
            (swagger/swagger-spec-resource
             (swagger/swagger-spec {:info {:title "Phonebook"
                                           :version "1.0"
                                           :description "A simple resource example"}
                                    :host (config/host config :phonebook)
                                    :schemes [(-> config :phonebook :scheme)]
                                    :basePath ""}
                                   (-> phonebook :api :routes))))
           (tag ::phonebook-swagger-spec))]

      ;;Boring specs
      [["/spec/rfc" :rfc] (rfc)]]]))

(defn new-docsite [& {:as opts}]
  (-> (map->Docsite opts)
      (using [:phonebook])
      (co-using [:router])))



