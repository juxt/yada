;; Copyright © 2015, JUXT LTD.

(ns yada.dev.demo
  (:require
   [bidi.bidi :refer (handler RouteProvider path-for)]
   [schema.core :as s]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [yada.core :refer (make-async-handler)]
   [hiccup.core :refer (html h) :rename {h escape-html}]
   [markdown.core :as markdown]
   [com.stuartsierra.component :refer (using)]
   [tangrammer.component.co-dependency :refer (co-using)]))

(defprotocol Example
  (description [_] "Return description")
  (resource-map [_] "Return handler")
  (request [_] "Return request sent to handler")
  (http-spec [_] "Which section of the HTTP spec does this relate to"))

(defrecord BodyAsString []
  Example
  (description [_] "The simplest resource-map contains a constant body value, which is returned in the response.")
  (resource-map [_] '{:body "Hello World!"})
  (request [_] {:method :get})
  (http-spec [_] nil))

(defrecord ServiceUnavailable []
  Example
  (description [_] "Return a 503 due to the service being unavailable.")
  (resource-map [_] '{:service-available? false})
  (request [_] {:method :get})
  (http-spec [_] "10.5.4"))

(defrecord ServiceUnavailableAsync []
  Example
  (description [_] "Return a 503 due to the service being
  unavailable. The availability of the service is determined
  asynchronously. In this test, a short-lived sleep is used to simulate
  the effect of asynchronously checking service availability.")
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) false)})
  (request [_] {:method :get})
  (http-spec [_] nil))

(defrecord DisallowedMethod []
  Example
  (description [_] "Return a 405 by using the GET method when only the POST method is allowed.")
  (resource-map [_] '{:allowed-method? #{:post}})
  (request [_] {:method :get})
  (http-spec [_] nil))

(defrecord ResourceDoesNotExist []
  Example
  (description [_] "Return a 404 because the resource does not exist")
  (resource-map [_] '{:resource nil})
  (request [_] {:method :get})
  (http-spec [_] nil))

(defrecord ResourceDoesNotExistAsync []
  Example
  (description [_] "Return a 404 because the resource does not exist. In this example, the resource check is a function which returns a deferred value, simulating an asynchronous call (for example, to a database)")
  (resource-map [_] '{:resource (fn [opts] (future (Thread/sleep 500)) false)})
  (request [_] {:method :get})
  (http-spec [_] nil))

(defn title [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn path [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn ->meth
  [m]
  (case m
    :get "GET"
    :post "POST"))

(defn index [routes handlers]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Yada Demo"]
     [:link {:href "/bootstrap/css/bootstrap.min.css" :rel "stylesheet"}]
     [:link {:href "/bootstrap/css/bootstrap-theme.min.css" :rel "stylesheet"}]
     [:link {:href "/static/css/style.css" :rel "stylesheet"}]
     (slurp (io/resource "shim.html"))
     [:script {:src "/static/js/demo.js"}]]
    [:body
     [:div.container
      [:div.row
       [:div#sidebar-menu.col-md-3.hidden-xs.hidden-sm
        [:ul.main-menu.nav.nav-stacked.affix
         (map-indexed
          (fn [ix h]
            [:li [:a {:href (str "#demo-" ix)} (title h)]])
          handlers)]]

       [:div.col-md-9 {:role "main"}
        [:div#intro
         (markdown/md-to-html-string (slurp (io/resource "intro.md")))
         ]
        (map-indexed
         (fn [ix h]
           (let [url (path-for routes (keyword (path h)))]
             [:div.handler
              [:h2 [:a {:name (str "demo-" ix)}]  (title h)]
              [:p (description h)]

              [:div
               [:h3 "Resource"]
               [:p "A resource has been created on the server with the following options :-"]
               [:pre (escape-html (resource-map h))]]

              (let [{:keys [method]} (request h)]
                [:div
                 [:h3 "Request"]
                 [:p (format "Click on the %s button to run this example, and check the response below"
                             (->meth (:method (request h))))]
                 [:pre [:button.btn.btn-primary
                        {:type "button"
                         :onClick (format "tryIt('%s', '%s','%s')"
                                          (->meth (:method (request h)))
                                          url
                                          ix)}
                        (->meth method)] (format " %s HTTP/1.1" url)]



                 ])

              [:div {:id (str "response-" ix)}
               [:h3 "Response " [:button.btn
                {:type "button"
                 :onClick (format "clearIt('%s')" ix)}
                "Clear"]]

               [:table.table
                [:tbody
                 [:tr
                  [:td "Status"]
                  [:td.status "0"]]
                 [:tr
                  [:td "Body"]
                  [:td [:textarea.body {:cols 80 :rows 2} ""]]]]]]

              (when-let [specref (http-spec h)]
                [:div
                 [:p [:a {:href (str "http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec" specref)} "Relevant section in HTTP spec (RFC 2616)"]]]

                )
              [:div ]

              [:hr]]))
         handlers)]]]

     [:script {:src "/jquery/jquery.min.js"}]
     [:script {:src "/bootstrap/js/bootstrap.min.js"}]]]))

(defrecord DemoApiService [router handlers]
  RouteProvider
  (routes [this]
    ["/demo/"
     (vec
      (concat
       (for [h handlers]
         [(path h) (handler (keyword (path h))
                            (make-async-handler (eval (resource-map h))))])
       [["index.html"
         (handler
          ::index
          (fn [_]
            {:status 200
             :headers {"content-type" "text/html;charset=utf-8"}
             :body (index (:routes @router) handlers)
             }))]]))]))

(defn new-demo-api-service [& {:as opts}]
  (-> (->> opts
           (merge {:handlers [(->BodyAsString)
                              (->ServiceUnavailable)
                              (->ServiceUnavailableAsync)
                              (->DisallowedMethod)
                              (->ResourceDoesNotExist)
                              (->ResourceDoesNotExistAsync)]})
        (s/validate {:handlers [(s/protocol Example)]})
        map->DemoApiService)
      (using [])
      (co-using [:router])))
