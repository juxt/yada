;; Copyright © 2015, JUXT LTD.

(ns yada.dev.security
  (:require
   [ring.middleware.cookies :refer [cookies-request cookies-response]]
   [bidi.bidi :refer [RouteProvider]]
   [buddy.sign.jws :as jws]
   [clj-time.core :as time]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [hiccup.core :refer (html)]
   [modular.bidi :refer (path-for)]
   [schema.core :as s]
   [yada.yada :as yada :refer [yada resource as-resource]]
   [yada.security :refer [verify-with-scheme]]
   [bidi.bidi :refer [tag]]))

(defn hello []
  (yada "Hello World!\n"))

(defn- login-form-html [fields]
  (html
   [:div
    [:style "* {margin:2px;padding:2px}"]
    [:h1 "Login form"]
    [:form {:method :post}
     (for [{:keys [label type name]} fields]
       [:div
        [:label {:for name} label]
        [:input {:type type :name name}]])
     [:div [:input {:type :submit :name :submit :value "Login"}]]]]))

(defn- login-form-parameters [fields]
  {:form
   (into {:submit String}
         (for [f fields] [(keyword (:name f)) String]))})

;; TODO: cookie expiry not seen in Chrome Network/Cookies Expires
;; column, investigate!

(defn login-form [fields]
  ;; Here we provide the fields as an argument, once. They serve 2
  ;; purposes: Generating the login form AND declaring the POST
  ;; parameters. This is a good example of cohesion. Instead of
  ;; duplicating field names (thus creating an implicit coupling
  ;; between the login form and form processor), we share them.
  (yada
   (resource
    {:methods
     {:get
      {:produces "text/html"
       :response (login-form-html fields)
       }
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
                                ;; TODO: Schema check context
                                :cookies {"session" cookie}
                                :body (format "Thanks %s!" (get-in ctx [:parameters :form :user]))))
                       (format "Login failed"))))
       :consumes "application/x-www-form-urlencoded"
       :produces "text/plain"}}})))

(defn logout [cookie-name body]
  (yada
   (resource
    {:methods
     {:get
      {:produces "text/html"
       :response (fn [ctx]
                   (->
                    (assoc (:response ctx)
                           :cookies {cookie-name {:value "" :expires 0}}
                           :body (body ctx))))}}})))

(defmethod verify-with-scheme "Custom"
  [ctx {:keys [verify]}]
  ;; TODO: Badly need cookie support
  (let [auth (some->
              (get-in ctx [:cookies "session"])
              (jws/unsign "secret"))]
    (infof "auth is %s" auth)
    auth
    ))

(s/defrecord SecurityExamples []
  RouteProvider
  (routes [_]
    (try
      ["/security"
       [["/basic"
         (yada
          (resource
           (merge (into {} (as-resource "SECRET!"))
                  {:access-control
                   {:realm "accounts"
                    :scheme "Basic"
                    :verify (fn [[user password]]
                              (when (= [user password] ["scott" "tiger"])
                                {:user "scott"
                                 :roles #{"accounts/view"}}))
                    :authorization {:methods {:get true}}}})))]

        ["/cookie"

         {"/login.html"
          (login-form
           [{:label "User" :type :text :name "user"}
            {:label "Password" :type :password :name "password"}])

          "/secret.html"
          (yada
           (resource
            {:access-control
             {:realm "accounts"
              :scheme "Custom"
              :verify identity
              :authorization {:methods {:get [:and
                                              "accounts/view"
                                              "accounts/view"]}}}
             :methods {:get "SECRET!"}}))

          "/logout"
          (logout "session" (fn [ctx]
                              (format "Logged out")))

          }]]]
      (catch Throwable e
        (errorf e "Getting exception on security example routes")
        ["/security/cookie/secret.html" (yada (str e))]
        ))))

(defn new-security-examples [config]
  (map->SecurityExamples {}))

