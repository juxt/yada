;; Copyright © 2015, JUXT LTD.

(ns yada.dev.examples
  (:require
   [bidi.bidi :refer (handler RouteProvider path-for alts)]
   [bidi.ring :refer (redirect)]
   [schema.core :as s]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cheshire.core :as json]
   [yada.core :refer (make-async-handler)]
   [hiccup.core :refer (html h) :rename {h escape-html}]
   [markdown.core :as markdown]
   [com.stuartsierra.component :refer (using Lifecycle)]
   [tangrammer.component.co-dependency :refer (co-using)]
   [ring.mock.request :refer (request) :rename {request mock-request}]))

(defn basename [r]
  (last (string/split (.getName (type r)) #"\.")))

(defprotocol Example
  (resource-map [_] "Return handler")
  (request [_] "Return request sent to handler")
  (path [_] "Where a resource is mounted")
  (path-args [_] "Any path arguments to use in the URI")
  (expected-response [_] "What the response should be")
  (http-spec [_] "Which section of an RFC does this relate to"))

(defrecord BodyAsString []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord StatusAndHeaders []
  Example
  (resource-map [_] '{:status 280
                      :headers {"content-type" "text/plain;charset=utf-8"
                                "x-extra" "foo"}
                      :body "Look, headers ^^^"})
  (request [_] {:method :get})
  (expected-response [_] {:status 280}))

(defrecord DynamicBody []
  Example
  (resource-map [_] '{:body (fn [ctx] "Hello World!")})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord AsyncBody []
  Example
  (resource-map [_] '{:body (fn [ctx]
                              (future (Thread/sleep 500)
                                      "Hello World!"))})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; TODO Async body options (lots more options than just this one)

(comment
  ;; client's content type already indicates content, e.g. application/json
  ;; which is already accessible via a delay (or equiv.)
  {:post (fn [ctx]
           ;; this is just like a normal Ring handler.

           ;; the main benefit is that state is available via deref,
           ;; the content-type is already handled

           (let [entity @(get-in ctx [:request :entity])]
             ;; create entity in database
             ;; construct new uri, redirect to it

             ;; the http diag says on POST :-
             ;; if POST, if redirect 303,

             ;; just use ring's redirect-after-post, using a location
             ;; header, constructed with bidi create user 741238, then
             ;; redirect
             (redirect (path-for routes :user 741238))

             ;; otherwise, if new resource? reply with 201
             (created (path-for routes :user 741238))
             => {:status 201 :headers {:location (path-for routes :user 741238)}}
             ;; otherwise return a 204, or a 200 if there's a body

             ;; you can always return a 300 too, if multiple representations
             )

           )})

;; Conneg

(def simple-body-map
  '{:body {"text/html" (fn [ctx] "<h1>Hello World!</h1>")
           "text/plain" (fn [ctx] "Hello World!")}} )

(defrecord BodyContentTypeNegotiation []
  Example
  (resource-map [_] simple-body-map)
  (request [_] {:method :get
                :headers {"Accept" "text/html"}})
  (expected-response [_] {:status 200}))

(defrecord BodyContentTypeNegotiation2 []
  Example
  (resource-map [_] simple-body-map)
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; Resource metadata (conditional requests)

(defrecord ResourceExists []
  Example
  (resource-map [_] '{:resource true})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceFunction []
  Example
  (resource-map [_] '{:resource (fn [req] true)})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceExistsAsync []
  Example
  (resource-map [_] '{:resource (fn [req] (future (Thread/sleep 500) true))})
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceDoesNotExist []
  Example
  (resource-map [_] '{:resource false})
  (request [_] {:method :get})
  (expected-response [_] {:status 404}))

(defrecord ResourceDoesNotExistAsync []
  Example
  (resource-map [_] '{:resource (fn [opts] (future (Thread/sleep 500) false))})
  (request [_] {:method :get})
  (expected-response [_] {:status 404}))

;; Resource State

(defrecord PathParameter []
  Example
  (resource-map [_] '{:body (fn [ctx] (format "Account number is %s" (-> ctx :request :route-params :account)))})
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceState []
  Example
  (resource-map [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))})
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceStateWithBody []
  Example
  (resource-map [_] '{:resource (fn [{{account :account} :route-params}]
                                  (when (== account 17382343)
                                    {:state {:balance 1300}}))
                      :body {"text/plain" (fn [ctx] (format "Your balance is ฿%s " (-> ctx :resource :state :balance)))}
                      })
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

(defrecord ResourceStateTopLevel []
  Example
  (resource-map [_] '{:state (fn [ctx] {:accno (-> ctx :request :route-params :account)})})
  (path [r] [(basename r) "/" [long :account]])
  (path-args [_] [:account 17382343])
  (request [_] {:method :get})
  (expected-response [_] {:status 200}))

;; Conditional GETs

(defrecord ConditionalGet [start-time]
  Example
  (resource-map [_] {:body "Hello World!"
                     :resource {:last-modified start-time}
                     })
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; POSTS

#_(defrecord PostNewResource []
  Example
  (resource-map [_] '{})
  (request [_] {:method :get
                :headers {"Accept" "text/plain"}})
  (expected-response [_] {:status 200}))

;; Conditional Requests

(defrecord PutResourceMatchedEtag []
  Example
  (resource-map [_] '{:resource {:etag "58614618"}
                      :put true})
  (request [_] {:method :put
                :headers {"If-Match" "58614618"}})
  (expected-response [_] {:status 204})
  (http-spec [_] ["7232" "3.1"]))

(defrecord PutResourceUnmatchedEtag []
  Example
  (resource-map [_] '{:resource {:etag "58614618"}
                      :put true})
  (request [_] {:method :put
                :headers {"If-Match" "c668ab6b"}})
  (expected-response [_] {:status 412})
  (http-spec [_] ["7232" "3.1"]))

;; Misc

(defrecord ServiceUnavailable []
  Example
  (resource-map [_] '{:service-available? false})
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableAsync []
  Example
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) false)})
  (request [_] {:method :get})
  (expected-response [_] {:status 503}))

(defrecord ServiceUnavailableRetryAfter []
  Example
  (resource-map [_] '{:service-available? 120})
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter2 []
  Example
  (resource-map [_] '{:service-available? (constantly 120)})
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter3 []
  Example
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) 120)})
  (request [_] {:method :get})
  (expected-response [_] {:status 503})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord DisallowedPost []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (request [_] {:method :post})
  (expected-response [_] {:status 405}))

(defrecord DisallowedGet []
  Example
  (resource-map [_] '{:post true})
  (request [_] {:method :get})
  (expected-response [_] {:status 405}))

(defrecord DisallowedPut []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (request [_] {:method :put})
  (expected-response [_] {:status 405}))

(defrecord DisallowedDelete []
  Example
  (resource-map [_] '{:post true})
  (request [_] {:method :delete})
  (expected-response [_] {:status 405}))

(defn title [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn get-path [r]
  (or
   (try (path r) (catch AbstractMethodError e))
   (last (string/split (.getName (type r)) #"\."))))

(defn get-path-args [r]
  (or
   (try (path-args r) (catch AbstractMethodError e))
   []))

(defn description [r]
  (when-let [s (io/resource (str "examples/pre/" (title r) ".md"))]
    (markdown/md-to-html-string (slurp s))))

(defn post-description [r]
  (when-let [s (io/resource (str "examples/post/" (title r) ".md"))]
    (markdown/md-to-html-string (slurp s))))

(defn ->meth
  [m]
  (case m
    :get "GET"
    :put "PUT"
    :delete "DELETE"
    :post "POST"))

(defn spaced [t]
  (string/join " " (re-seq #"[A-Z2-9][a-z]*" t)))

(defn bootstrap-head [{:keys [title extra]}]
  [:head
   (concat
    [[:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title title]
     [:link {:href "/bootstrap/css/bootstrap.min.css" :rel "stylesheet"}]
     [:link {:href "/bootstrap/css/bootstrap-theme.min.css" :rel "stylesheet"}]
     [:link {:href "/static/css/style.css" :rel "stylesheet"}]
     (slurp (io/resource "shim.html"))]
    extra)])

(defn container [& content]
  [:div.container content])

(defn row [& content]
  [:div.row content])

(defn sidebar-menu [& content]
  [:div#sidebar-menu.col-md-3.hidden-xs.hidden-sm
   [:p "All examples"]
   [:ul.main-menu.nav.nav-stacked
    content]])

(defn tests [routes handlers]
  (let [header [:button.btn.btn-primary {:onClick "testAll()"} "Repeat tests"]]
    (html
     [:html
      (bootstrap-head {:title "Yada Example Tests"})
      [:body
       [:div#intro
        (markdown/md-to-html-string (slurp (io/resource "tests.md")))]

       header

       [:table.table
        [:thead
         [:tr
          [:th "#"]
          [:th "Title"]
          [:th "Expected response"]
          [:th "Status"]
          [:th "Headers"]
          [:th "Body"]
          [:th "Result"]
          ]]
        [:tbody
         (map-indexed
          (fn [ix h]
            (let [url (apply path-for routes (keyword (basename h)) (get-path-args h))
                  {:keys [method headers]} (request h)]
              [:tr {:id (str "test-" (title h))}
               [:td (inc ix)]
               [:td [:a {:href (format "%s#%s" (path-for routes ::index) (title h))} (title h)]]
               [:td (:status (try (expected-response h) (catch AbstractMethodError e)))]
               [:td.status ""]
               [:td.headers ""]
               [:td [:textarea.body ""]]

               [:td.result ""]
               [:td [:button.btn.test
                     {:onClick (format "testIt('%s','%s','%s',%s,%s)"
                                       (->meth method)
                                       url
                                       (title h)
                                       (json/encode headers)
                                       (json/encode (or (try (expected-response h) (catch AbstractMethodError e))
                                                        {:status 200}))
                                       )} "Run"]]]))
          handlers)]]

       [:script {:src "/jquery/jquery.min.js"}]
       [:script {:src "/bootstrap/js/bootstrap.min.js"}]
       [:script {:src "/static/js/tests.js"}]]])))

(defn index [routes handlers]
  (html
   [:html
    (bootstrap-head {:title "Yada Examples"})
    [:body
     (sidebar-menu
      (map
       (fn [h]
         [:li.small [:a {:href (str "#" (title h))} (spaced (title h))]])
       handlers))

     [:div.col-md-9 {:role "main"}
      [:a {:name "top"}]
      [:div#intro
       (markdown/md-to-html-string (slurp (io/resource "intro.md")))]

      (map
       (fn [h]
         (let [url (apply path-for routes (keyword (basename h)) (get-path-args h))]
           [:div
            [:p [:a {:name (str (title h))}] "&nbsp;"]
            [:div
             [:div.example
              [:h3 (spaced (title h))]
              [:p (description h)]

              [:div
               [:h4 "Resource Map"]
               [:pre (escape-html (with-out-str (clojure.pprint/pprint (resource-map h))))]]

              #_[:div
               [:h4 "Bidi route"]
               [:pre (str ["/" [[(get-path h) 'handler]]])]
               ]

              (let [{:keys [method headers]} (request h)]
                [:div
                 [:h4 "Request"]
                 [:pre
                  (->meth method) (format " %s HTTP/1.1" url)
                  (for [[k v] headers] (format "\n%s: %s" k v))]
                 [:p
                  [:button.btn.btn-primary
                   {:type "button"
                    :onClick (format "tryIt('%s','%s','%s',%s)"
                                     (->meth method)
                                     url
                                     (title h)
                                     (json/encode headers))}
                   "Try it"]
                  " "
                  [:button.btn
                   {:type "button"
                    :onClick (format "clearIt('%s')" (title h))}
                   "Reset"]]])

              [:div {:id (str "response-" (title h))}
               [:h4 "Response"]

               [:table.table
                [:tbody
                 [:tr
                  [:td "Status"]
                  [:td.status ""]]
                 [:tr
                  [:td "Headers"]
                  [:td.headers ""]]
                 [:tr
                  [:td "Body"]
                  [:td [:textarea.body ""]]]
                 ]]]

              (when-let [text (post-description h)]
                [:p text])

              (when-let [[spec sect]
                         (try (http-spec h)
                              (catch AbstractMethodError e))]
                [:div
                 [:p [:a {:href (format "/static/spec/rfc%s.html#section-%s"
                                        spec sect)
                          :target "_spec"}
                      (format "Section %s in RFC %s" sect spec)]]])
              ]]]))
       handlers)

      (row
       [:div.container {:style "margin-top: 20px"} [:p [:a {:href "#top"} "Back to top"]]])
      ]

     [:script {:src "/jquery/jquery.min.js"}]
     [:script {:src "/bootstrap/js/bootstrap.min.js"}]
     [:script {:src "/static/js/examples.js"}]]]))

(defn ok [body]
  {:status 200
   :headers {"content-type" "text/html;charset=utf-8"}
   :body body})

(defrecord ExamplesService [router]
  Lifecycle
  (start [component] (assoc component :start-time (java.util.Date.)))
  (stop [component] component)

  RouteProvider
  (routes [component]
    (let [handlers [(->BodyAsString)
                    (->StatusAndHeaders)
                    (->DynamicBody)
                    (->AsyncBody)
                    (->BodyContentTypeNegotiation)
                    (->BodyContentTypeNegotiation2)
                    (->ResourceExists)
                    (->ResourceFunction)
                    (->ResourceExistsAsync)
                    (->ResourceDoesNotExist)
                    (->ResourceDoesNotExistAsync)
                    (->PathParameter)
                    (->ResourceState)
                    (->ResourceStateWithBody)
                    (->ResourceStateTopLevel)

                    (->ConditionalGet (:start-time component))

                    (->PutResourceMatchedEtag)
                    (->PutResourceUnmatchedEtag)
                    (->ServiceUnavailable)
                    (->ServiceUnavailableAsync)
                    (->ServiceUnavailableRetryAfter)
                    (->ServiceUnavailableRetryAfter2)
                    (->ServiceUnavailableRetryAfter3)
                    (->DisallowedPost)
                    (->DisallowedGet)
                    (->DisallowedPut)
                    (->DisallowedDelete)
                    ]]
      (s/validate [(s/protocol Example)] handlers)
      ["/examples"
       [["/"
         (vec
          (concat
           (for [h handlers]
             [(get-path h) (handler (keyword (basename h))
                                    (make-async-handler (eval (resource-map h))))])
           [["index.html"
             (handler
              ::index
              (fn [_]
                (ok
                 (index (:routes @router) handlers))))]
            ["tests.html"
             (handler
              ::tests
              (fn [_]
                (ok (tests (:routes @router) handlers))))
             ]
            ["" (redirect ::index)]]))]
        ["" (redirect ::index)]]]
      )))

(defn new-examples-service [& {:as opts}]
  (-> (->> opts
           (merge {})

           map->ExamplesService)
      (using [])
      (co-using [:router])))
