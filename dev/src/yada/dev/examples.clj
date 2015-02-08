;; Copyright © 2015, JUXT LTD.

(ns yada.dev.examples
  (:require
   [bidi.bidi :refer (handler RouteProvider path-for)]
   [schema.core :as s]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cheshire.core :as json]
   [yada.core :refer (make-async-handler)]
   [hiccup.core :refer (html h) :rename {h escape-html}]
   [markdown.core :as markdown]
   [com.stuartsierra.component :refer (using)]
   [tangrammer.component.co-dependency :refer (co-using)]))

(defprotocol Example
  (resource-map [_] "Return handler")
  (request [_] "Return request sent to handler")
  (http-spec [_] "Which section of an RFC does this relate to"))

(defrecord BodyAsString []
  Example
  (resource-map [_] '{:body "Hello World!"})
  (request [_] {:method :get}))

(defrecord PutResourceMatchedEtag []
  Example
  (resource-map [_] '{:allowed-method? :put
                      :resource {:etag "58614618"}})
  (request [_] {:method :put
                :headers {"If-Match" "58614618"}})
  (http-spec [_] ["7232" "3.1"]))

(defrecord PutResourceUnmatchedEtag []
  Example
  (resource-map [_] '{:allowed-method? :put
                      :resource {:etag "58614618"}})
  (request [_] {:method :put
                :headers {"If-Match" "c668ab6b"}})
  (http-spec [_] ["7232" "3.1"]))

(defrecord ServiceUnavailable []
  Example
  (resource-map [_] '{:service-available? false})
  (request [_] {:method :get})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableAsync []
  Example
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) false)})
  (request [_] {:method :get}))

(defrecord ServiceUnavailableRetryAfter []
  Example
  (resource-map [_] '{:service-available? 120})
  (request [_] {:method :get})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter2 []
  Example
  (resource-map [_] '{:service-available? (constantly 120)})
  (request [_] {:method :get})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord ServiceUnavailableRetryAfter3 []
  Example
  (resource-map [_] '{:service-available? #(future (Thread/sleep 500) 120)})
  (request [_] {:method :get})
  (http-spec [_] ["7231" "6.6.4"]))

(defrecord DisallowedMethod []
  Example
  (resource-map [_] '{:allowed-method? #{:post}})
  (request [_] {:method :get}))

(defrecord ResourceDoesNotExist []
  Example
  (resource-map [_] '{:resource nil})
  (request [_] {:method :get}))

(defrecord ResourceDoesNotExistAsync []
  Example
  (resource-map [_] '{:resource (fn [opts] (future (Thread/sleep 500) false))})
  (request [_] {:method :get}))

(defn title [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn path [r]
  (last (string/split (.getName (type r)) #"\.")))

(defn description [r]
  (if-let [s (io/resource (str "examples/" (title r) ".md"))]
    (markdown/md-to-html-string (slurp s))
    (throw (ex-info (format "Failed to find description for %s" (title r)) {}))))

(defn ->meth
  [m]
  (case m
    :get "GET"
    :put "PUT"
    :delete "DELETE"
    :post "POST"))

(defn spaced [t]
  (string/join " " (re-seq #"[A-Z2-9][a-z]*" t)))

(defn index [routes handlers]
  (html
   [:html
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title "Yada Examples"]
     [:link {:href "/bootstrap/css/bootstrap.min.css" :rel "stylesheet"}]
     [:link {:href "/bootstrap/css/bootstrap-theme.min.css" :rel "stylesheet"}]
     [:link {:href "/static/css/style.css" :rel "stylesheet"}]
     (slurp (io/resource "shim.html"))
     [:script {:src "/static/js/examples.js"}]]
    [:body
     [:div.container
      [:div.row
       [:div#sidebar-menu.col-md-3.hidden-xs.hidden-sm
        [:ul.main-menu.nav.nav-stacked.affix
         (map-indexed
          (fn [ix h]
            [:li.small [:a {:href (str "#example-" ix)} (spaced (title h))]])
          handlers)]]

       [:div.col-md-6 {:role "main"}
        [:div#intro
         (markdown/md-to-html-string (slurp (io/resource "intro.md")))]

        (map-indexed
         (fn [ix h]
           (let [url (path-for routes (keyword (path h)))]
             [:div.handler
              [:h3 [:a {:name (str "example-" ix)}] (spaced (title h))]
              [:p (description h)]

              [:div
               [:h4 "Resource Map"]
               [:pre (escape-html (resource-map h))]]

              (let [{:keys [method headers]} (request h)]
                [:div
                 [:h4 "Request"]
                 [:p (format "Click on the %s button to run this example, and check the response below"
                             (->meth method))]
                 [:pre
                  [:button.btn.btn-primary
                   {:type "button"
                    :onClick (format "tryIt('%s','%s','%s',%s)"
                                     (->meth method)
                                     url
                                     ix (json/encode headers))}
                   (->meth method)] (format " %s HTTP/1.1" url)
                  (for [[k v] headers] (format "\n%s: \"%s\"" k v))]])

              [:div {:id (str "response-" ix)}
               [:h4 "Response " [:button.btn
                                 {:type "button"
                                  :onClick (format "clearIt('%s')" ix)}
                                 "Clear"]]

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
                  [:td [:textarea.body {:style "width: 100%" :rows 2} ""]]]]]]

              (when-let [[spec sect] (try (http-spec h)
                                          (catch AbstractMethodError e))]
                [:div
                 [:p [:a {:href (format "/static/spec/rfc%s.html#section-%s" spec sect)
                          :target "_spec"}
                      (format "Section %s in RFC %s" sect spec)]]]

                )
              [:div ]

              [:hr]]))
         handlers)]]]

     [:script {:src "/jquery/jquery.min.js"}]
     [:script {:src "/bootstrap/js/bootstrap.min.js"}]]]))

(defrecord ExamplesService [router handlers]
  RouteProvider
  (routes [this]
    ["/examples/"
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

(defn new-examples-service [& {:as opts}]
  (-> (->> opts
           (merge {:handlers
                   [(->BodyAsString)
                    (->PutResourceMatchedEtag)
                    (->PutResourceUnmatchedEtag)
                    (->ServiceUnavailable)
                    (->ServiceUnavailableAsync)
                    (->ServiceUnavailableRetryAfter)
                    (->ServiceUnavailableRetryAfter2)
                    (->ServiceUnavailableRetryAfter3)
                    (->DisallowedMethod)
                    (->ResourceDoesNotExist)
                    (->ResourceDoesNotExistAsync)]})
           (s/validate {:handlers [(s/protocol Example)]})
           map->ExamplesService)
      (using [])
      (co-using [:router])))
