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
   [yada.yada :as yada :refer [yada resource]]
   [bidi.bidi :refer [tag]]
   yada.resources.sse))

(defn hello []
  (yada "Hello World!\n"))

(defn hello-atom []
  (yada (atom "Hello World!\n")))

(defn hello-swagger []
  (swaggered ["/hello" (yada "Hello World!\n")]
             {:info {:title "Hello World!"
                     :version "1.0"
                     :description "A swaggered String"}
              :basePath "/hello-swagger"}
             ))

(defn hello-atom-swagger []
  (swaggered ["/hello" (hello-atom)]
             {:info {:title "Hello World!"
                     :version "1.0"
                     :description "A String inside a Clojure atom, swaggered"}
              :basePath "/hello-atom-swagger"}
             ))

(defn say-hello [ctx]
  (str "Hello " (get-in ctx [:parameters :query :p]) "!\n"))

(defn hello-parameters []
  (yada
   (resource
    {:methods
     {:get
      {:parameters {:query {:p String}}
       :produces "text/plain"
       :response say-hello}}})))

(defn hello-languages []
  (yada
   (resource
    {:methods
     {:get
      {:produces [{:media-type "text/plain"
                   :language #{"zh-ch" "en"}}]
       :response (fn [ctx]
                   (case (yada/language ctx)
                     "zh-ch" "你好世界!\n"
                     "en" "Hello World!\n"))}}})))

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

(defn index []
  (-> (new-template-resource
       "templates/page.html"
       (fn [ctx]
         {:homeref (:href (yada/uri-for ctx :yada.dev.docsite/index))
         :content
         (html
          [:div.container
           [:h2 "Demo: Hello World!"]
           [:ul
            [:li [:a {:href (:href (yada/uri-for ctx ::hello))} "Hello!"]
             " - the simplest possible demo, showing the effect of " [:span.yada "yada"] " on a simple string"]

            [:li [:a {:href
                      (format "%s/index.html?url=%s/swagger.json"
                              (:href (yada/uri-for ctx :swagger-ui))
                              (:href (yada/uri-for ctx ::hello-swagger)))}
                  "Hello Swagger!"]
             " - demonstration of the Swagger interface on a simple string"]

            [:li [:a {:href (:href (yada/uri-for ctx ::hello-atom))} "Hello atom!"]
             " - demonstrating the use of Clojure's reference types to manage mutable state"]

            [:li [:a {:href
                      (format "%s/index.html?url=%s/swagger.json"
                              (:href (yada/uri-for ctx :swagger-ui))
                              (:href (yada/uri-for ctx ::hello-atom-swagger)))}
                  "Hello Swaggatom!"]
             " - demonstration of the Swagger interface on an atom"]]
           
           [:p [:a {:href (:href (yada/uri-for ctx ::index))} "Index"]]])}))
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

              ;; Parameters
              ["/hello-parameters" (tag (hello-parameters) ::hello-parameters)]

              ;; Content-negotiation
              ["/hello-languages" (tag (hello-languages) ::hello-languages)]

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
            (catch Throwable e
              (errorf e "Getting exception on hello routes")
              ["" false]
              ))))

(defn new-hello-world-example [config]
  (map->HelloWorldExample {:config config}))
