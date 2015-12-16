;; Copyright © 2015, JUXT LTD.

(ns yada.dev.hello
  (:require
   [bidi.bidi :refer [RouteProvider]]
   [clojure.core.async :refer (chan go <! >! timeout go-loop)]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [schema.core :as s]
   [yada.dev.config :as config]
   [yada.dev.template :refer [new-template-resource]]
   [yada.swagger :refer [swaggered]]
   [yada.yada :as yada :refer [yada]]
   [bidi.bidi :refer [tag]]
   yada.resources.sse))

(defn hello []
  (yada "Hello World!\n" #_{:error-handler identity}))

(defn hello-atom []
  (yada (atom "Hello World!\n") #_{:error-handler identity}))

(defn hello-swagger []
  (swaggered {:info {:title "Hello World!"
                     :version "1.0"
                     :description "Demonstrating yada + swagger"}
              :basePath "/hello-swagger"}
             ["/hello" (hello)]))

(defn hello-atom-swagger []
  (swaggered {:info {:title "Hello World!"
                     :version "1.0"
                     :description "Demonstrating yada + swagger"}
              :basePath "/hello-atom-swagger"}
             ["/hello" (hello-atom)]))

(defn hello-sse [ch]
  (go-loop [t 0]
    (when (>! ch (format "Hello World! (%d)" t))
      (<! (timeout 100))
      (recur (inc t))))
  (yada/yada ch))

(defn hello-different-origin-1 [config]
  ;; TODO: Replace with {:error-handler nil} and have implementation
  ;; check with contains? for key
  (yada "Hello World!\n" #_{:error-handler identity
                          :access-control {:allow-origin (config/cors-demo-origin config)
                                           :allow-headers #{"authorization"}}
                          :representations [{:media-type "text/plain"}]}))

(defn hello-different-origin-2 [config]
  (yada "Hello World!\n" #_{:error-handler identity
                          :access-control {:allow-origin true ; only show incoming origin
                                           :allow-credentials true}
                          :representations [{:media-type "text/plain"}]}))

(defn hello-different-origin-3 [config]
  (yada "Hello World!\n" #_{:error-handler identity
                          :access-control {:allow-origin "*"
                                           :allow-credentials true}
                          :representations [{:media-type "text/plain"}]}))

(defn index [*router]
  (-> (new-template-resource
       "templates/page.html"
       (delay      ; Delay because our *router won't be delivered yet
        {:homeref (path-for @*router :yada.dev.docsite/index)
         :content
         (html
          [:div.container
           [:h2 "Demo: Hello World!"]
           [:ul
            [:li [:a {:href (path-for @*router ::hello)} "Hello!"]
             " - the simplest possible demo, showing the effect of " [:span.yada "yada"] " on a simple string"]

            [:li [:a {:href
                      (format "%s/index.html?url=%s/swagger.json"
                              (path-for @*router :swagger-ui)
                              (path-for @*router ::hello-swagger))}
                  "Hello Swagger!"]
             " - demonstration of the Swagger interface on a simple string"]

            [:li [:a {:href (path-for @*router ::hello-atom)} "Hello atom!"]
             " - demonstrating the use of Clojure's reference types to manage mutable state"]

            [:li [:a {:href
                      (format "%s/index.html?url=%s/swagger.json"
                              (path-for @*router :swagger-ui)
                              (path-for @*router ::hello-atom-swagger))}
                  "Hello Swaggatom!"]
             " - demonstration of the Swagger interface on an atom"]]
           
           [:p [:a {:href (path-for @*router ::index)} "Index"]]])}))
      (assoc :id ::index)
      yada))

(s/defrecord HelloWorldExample [channel
                                config :- config/ConfigSchema]
  Lifecycle
  (start [component]
    (assoc component :channel (chan 10)))

  (stop [component] component)

  RouteProvider
  (routes [_]
          (try
            [""
             [["/hello" (tag (hello) ::hello)]
              ["/hello-atom" (tag (hello-atom) ::hello-atom)]

              ;; Swagger
              ["/hello-swagger" (tag (hello-swagger) ::hello-swagger)]
              ["/hello-atom-swagger" (tag (hello-atom-swagger) ::hello-atom-swagger)]

              ;; Realtime
              ["/hello-sse" (hello-sse channel)]

              ;; Remote access
              ["/hello-different-origin/1" (hello-different-origin-1 config)]
              ["/hello-different-origin/2" (hello-different-origin-2 config)]
              ["/hello-different-origin/3" (hello-different-origin-3 config)]
              ]]
            (catch Exception e
              (errorf e "Getting exception on hello routes")
              ["" false]
              ))))

(defn new-hello-world-example [config]
  (map->HelloWorldExample {:config config}))
