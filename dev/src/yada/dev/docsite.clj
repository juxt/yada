;; Copyright © 2015, JUXT LTD.

(ns yada.dev.docsite
  (:require
   [byte-streams :as b]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [bidi.bidi :refer [RouteProvider tag Matched] :as bidi]
   [bidi.vhosts :refer [uri-for coerce-to-vhost]]
   [com.stuartsierra.component :refer [using]]
   [hiccup.core :refer [html h]]
   [markdown.core :refer [md-to-html-string]]
   [schema.core :as s]
   [yada.yada :refer [handler resource href-for]]
   [yada.dev.config :as config]
   [yada.resources.file-resource :refer [new-directory-resource]]
   [yada.resources.classpath-resource :refer [new-classpath-resource]]
   [yada.schema :as ys])
  (:import
   [com.stuartsierra.component SystemMap]))

(def titles
  {2046 "Multipurpose Internet Mail Extensions (MIME) Part Two: Media Types"
   2183 "Communicating Presentation Information in Internet Messages: The Content-Disposition Header Field"
   2616 "Hypertext Transfer Protocol -- HTTP/1.1"
   4647 "Matching of Language Tags"
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
      ((handler source) req))))

(defn index []
  (handler
   (resource
    {:methods
     {:get
      {:produces "text/html"
       :response
       (fn [ctx]
         (html
          [:div.container
           [:h2 "Welcome to " [:span.yada "yada"] "!"]

           [:p [:a {:href (href-for ctx :yada.dev.manual/manual)} "The yada user manual"]]

           #_[:ol
            [:li [:a {:href (yada/href-for ctx ::manual)}
                  "The " [:span.yada "yada"] " manual"]
             " — the single authority on all things " [:span.yada "yada"]]

            [:li "Examples — self-contained apps for you to explore"
             [:ul
              [:li [:a {:href (yada/href-for ctx :yada.dev.hello/index)} "Hello World!"] " — to introduce " [:span.yada "yada"] " in the proper way"]

              #_[:li
                 [:a {:href (yada/href-for ctx :phonebook.api/index)}
                  "Phonebook"]
                 [:a {:href
                      ;; TODO: use bidi's path-for
                      (format "%s/phonebook-swagger.html?url=%s"
                              (yada/href-for ctx :swagger-ui)
                              (:uri (yada/uri-info ctx ::phonebook-swagger-spec))

                              )}
                  " (Swagger)"]
                 " — to demonstrate custom records implementing standard HTTP methods"]

              [:li [:a {:href (yada/href-for ctx :yada.dev.security/index)} "Security"] " — to demonstrate authentication and authorization features"]

              [:li [:a {:href (yada/href-for ctx :yada.dev.async/sse-demo)} "SSE demo"] " — to demonstrate Server Sent Events"]

              ]]

            [:li [:a {:href (yada/href-for ctx :yada.dev.talks/index)} "Talks"]]

            [:li "Relevant standards"
             [:ul
              (for [i (sort (keys titles))]
                [:li [:a {:href (format "/spec/rfc%d" i)}
                      (format "RFC %d: %s" i (or (get titles i) ""))]])]]

            ]
           ]))}}}
    )))

#_(s/defrecord Docsite [config :- config/ConfigSchema]
  RouteProvider
  (routes [component]
    (try
      [""
       [["/favicon.ico" (yada nil)]
        ["/index.html" (index component)]

        ["/hello.html" (hello/index)]

        ["/nextroll" (yada #(inc (rand-int 6)))]

        ;;["/body.html" {:produces "text/html" :response (fn [] "Hello")}]

        ["/manual/"
         (yada (->
                (new-directory-resource
                 (io/file "manuscript")
                 {:custom-suffices
                  {"md" {:produces (ys/representation-seq-coercer
                                    [{:media-type #{"text/html" "text/plain;q=0.9"}
                                      :charset "utf-8"}])
                         :reader (fn [f rep]
                                   (cond
                                     (= (-> rep :media-type :name) "text/html")
                                     (html
                                      [:head
                                       [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
                                       [:title (.getName f)]
                                       [:style {:type "text/css"} (slurp (io/resource "style.css"))]]

                                      [:body
                                       [:p "The " [:span {:class "yada"} "yada"] " manual"]
                                       (md-to-html-string (slurp f)) \newline])
                                     :otherwise f))}
                   "org" {:produces (ys/representation-seq-coercer
                                     [{:media-type #{"text/plain"}}])}}
                  :index-files ["README.md"]})
                (conj [:id ::manual])))]

        ["/dir/" (yada (io/file "talks"))]
        ["/jar" (yada (new-classpath-resource "META-INF/resources/webjars/swagger-ui/2.2.6"))]

        ["/h1.html" (yada [:h1 "Heading"])]

        ["/500" (resource
                 {:methods {:get {:produces "text/html"
                                  :response (fn [ctx] (throw (new Exception "Ooh!")))}}
                  :responses {500 {:produces "text/plain"
                                   :response (fn [ctx] "Error, but I'm OK")}}})]

        ["/redirect-target" (resource {:id :foo :produces "text/html" :response "Hi\n"})]
        ["/redirect" (redirect :foo)]

        ["/dominic" (yada nil)]

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
          ["/greetings"
           [
            ["/hello" (yada "Hello World!\n")]
            ["/goodbye" (yada "Goodbye!\n")]
            ]
           ]
          {:info {:title "Hello World!"
                  :version "1.0"
                  :description "A greetings service"}
           :basePath "/api"})]

        ["/phonebook-api/swagger.json"
         (-> (yada
              (swagger/swagger-spec-resource
               (swagger/swagger-spec
                (-> phonebook :api :routes)
                {:info {:title "Phonebook"
                        :version "1.0"
                        :description "A simple resource example"}
                 :host (-> config :phonebook :vhosts first coerce-to-vhost :host)
                 :schemes [(-> config :phonebook :scheme)]
                 :tags [{:name "getters"
                         :description "All paths that support GET"}]
                 :basePath ""})))
             (tag ::phonebook-swagger-spec))]

        ;;Boring specs
        [["/spec/rfc" :rfc] (rfc)]

        ]]
      (catch Exception e
        ["/error" (fn [req] {:status 200 :body (str e)})]
        ))))

#_(defn new-docsite [& {:as opts}]
  (-> (map->Docsite opts)
      (using [:phonebook])
      ))
