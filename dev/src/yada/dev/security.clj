;; Copyright © 2015, JUXT LTD.

(ns yada.dev.security
  (:require
   [bidi.bidi :refer [RouteProvider tag]]
   [buddy.sign.jws :as jws]
   [clj-time.core :as time]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [modular.component.co-dependency.schema :refer [co-dep]]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [modular.bidi :refer (path-for)]
   [modular.component.co-dependency :refer (co-using)]
   [schema.core :as s]
   [yada.dev.template :refer [new-template-resource]]
   [yada.security :refer [verify-with-scheme]]
   [yada.yada :as yada :refer [yada resource as-resource]]
   [ring.middleware.cookies :refer [cookies-request cookies-response]])
  (:import [modular.bidi Router]))

(defn- login-form-parameters [fields]
  {:form
   (into {:submit String}
         (for [f fields] [(keyword (:name f)) String]))})

;; TODO: cookie expiry not seen in Chrome Network/Cookies Expires
;; column, investigate!

(defn login [fields *router]
  ;; Here we provide the fields as an argument, once. They serve 2
  ;; purposes: Generating the login form AND declaring the POST
  ;; parameters. This is a good example of cohesion. Instead of
  ;; duplicating field names (thus creating an implicit coupling
  ;; between the login form and form processor), we share them.
  (yada
   (resource
    {:id ::login
     :methods
     {:get
      {:produces "text/html"
       :response (html
                  [:div
                   [:style "* {margin:2px;padding:2px}"]
                   [:h1 "Login form"]
                   [:form {:method :post}
                    (for [{:keys [label type name]} fields]
                      [:div
                       [:label {:for name} label]
                       [:input {:type type :name name}]])
                    [:div [:input {:type :submit :name :submit :value "Login"}]]]])}
      
      :post
      {:parameters (login-form-parameters fields)
       :response (fn [ctx]
                   (let [params (get-in ctx [:parameters :form])]
                     (if (= ((juxt :user :password) params)
                            ["scott" "tiger"])
                       (let [expires (time/plus (time/now) (time/minutes 15))
                             jwt (jws/sign {:user "scott"
                                            :roles ["accounts/view"]
                                            :exp expires}
                                           "secret")
                             cookie {:value jwt
                                     :expires expires
                                     :http-only true}]
                         (assoc (:response ctx)
                                :cookies {"session" cookie}
                                :body (html
                                       [:h1 (format "Hello %s!" (get-in ctx [:parameters :form :user]))]
                                       [:p [:a {:href (path-for @*router ::secret)} "secret"]]
                                       [:p [:a {:href (path-for @*router ::logout)} "logout"]]
                                       )))
                       (assoc (:response ctx)
                              ;; It's possible the user was already logged in, in which case we log them out
                              :cookies {"session" {:value "" :expires 0}}
                              :body (html [:h1 "Login failed"]
                                          [:p [:a {:href (path-for @*router ::login)} "try again"]]
                                          [:p [:a {:href (path-for @*router ::secret)} "secret"]])))))

       :consumes "application/x-www-form-urlencoded"
       :produces "text/html"}}})))

(defmethod verify-with-scheme "Custom"
  [ctx {:keys [verify]}]
  (let [auth (some->
              (get-in ctx [:cookies "session"])
              (jws/unsign "secret"))]
    auth))

(defn build-routes [*router]
  (try
    ["/security"
     [
      ["/index.html"
       (-> (new-template-resource
            "templates/page.html"
            (delay
             {:homeref (path-for @*router :yada.dev.docsite/index)
              :content
              (html
               [:div.container
                [:h2 "Security examples"]
                [:p "The following exmples demonstrate the
                                    authentication and authorization
                                    features of yada. See " [:a
                                    {:href "https://github.com/juxt/yada/blob/master/dev/src/yada/dev/security.clj"} "demo
                                    code"] " for implementation
                                    details."]
                [:ul
                 [:li [:a {:href (path-for @*router ::basic)} "Basic"]]
                 [:li [:a {:href (path-for @*router ::login)} "Session"]]
                 [:li "Bearer (OAuth2) - coming soon"]]

                [:h4 "Login details for all examples"]
                [:p "Login with username "
                 [:tt "scott"]
                 " and password "
                 [:tt "tiger"]]
                ])}))
           (assoc :id ::index))]
      ["/basic"
       (yada
        (resource
         (merge (into {} (as-resource "SECRET!"))
                {:id ::basic
                 :access-control
                 {:realm "accounts"
                  :scheme "Basic"
                  :verify (fn [[user password]]
                            (when (= [user password] ["scott" "tiger"])
                              {:user "scott"
                               :roles #{"accounts/view"}}))
                  :authorization {:methods {:get "accounts/view"}}}})))]

      ["/cookie"

       {"/login.html"
        (login
         [{:label "User" :type :text :name "user"}
          {:label "Password" :type :password :name "password"}]
         *router)

        "/logout"
        (yada
         (resource
          {:id ::logout
           :methods
           {:get
            {:produces "text/html"
             :response (fn [ctx]
                         (->
                          (assoc (:response ctx)
                                 :cookies {"session" {:value "" :expires 0}}
                                 :body (html
                                        [:h1 "Logged out"]
                                        [:p [:a {:href (path-for @*router ::login)} "login"]]))))}}}))

        "/secret.html"
        (yada
         (resource
          {:id ::secret
           
           :methods {:get {:response (fn [ctx]
                                       (html
                                        [:h1 "Seek happiness"]
                                        [:p [:a {:href (path-for @*router ::logout)} "logout"]]))
                           :produces "text/html"}}

           :access-control
           {:scheme "Custom"
            :verify identity
            :authorization {:methods {:get [:and
                                            "accounts/view"
                                            "accounts/view"]}}}

           :responses {401 {:produces "text/html" ;; TODO: If we neglect to put in produces we get an error
                            :response (fn [ctx]
                                        (html
                                         [:h1 "Sorry"]
                                         [:p "You are not authorized yet"]
                                         [:p "Please " [:a {:href (path-for @*router ::login)} "login" ]]))}}}))}]]]

    (catch Throwable e
      (errorf e "Getting exception on security example routes")
      ["/security/cookie/secret.html" (yada (str e))]
      )))

(s/defrecord SecurityExamples [*router :- (co-dep Router)]
  RouteProvider
  (routes [_] (build-routes *router)))

(defn new-security-examples [config]
  (-> 
   (map->SecurityExamples {})
   (using [])
   (co-using [:router])))

