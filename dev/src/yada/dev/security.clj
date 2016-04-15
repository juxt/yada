;; Copyright © 2015, JUXT LTD.

(ns yada.dev.security
  (:require
   [aleph.http :as http]
   [bidi.bidi :refer [RouteProvider tag]]
   [bidi.ring :refer [redirect]]
   [bidi.vhosts :refer [uri-for]]
   [buddy.core.hash :as hash]
   [buddy.sign.jws :as jws]
   [buddy.sign.jwe :as jwe]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle using]]
   [hiccup.core :refer (html h)]
   [manifold.deferred :as d]
   [ring.util.codec :as codec]
   [schema.core :as s]
   yada.jwt
   [yada.dev.template :refer [new-template-resource]]
   [yada.security :refer [verify]]
   [yada.yada :as yada :refer [yada resource as-resource handler]]
   [yada.oauth :as oauth]
   )
  (:import [modular.bidi Router]
           [clojure.lang ExceptionInfo]))

(defn- login-form-parameters [fields]
  {:form
   (into {:submit String}
         (for [f fields] [(keyword (:name f)) String]))})

;; TODO: cookie expiry not seen in Chrome Network/Cookies Expires
;; column, investigate!

(defn login [fields secret]
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
                                            :roles ["secret/view"]
                                            :exp expires}
                                           secret)
                             cookie {:value jwt
                                     :expires expires
                                     :http-only true}]
                         (assoc (:response ctx)
                                :cookies {"session" cookie}
                                :body (html
                                       [:h1 (format "Hello %s!" (get-in ctx [:parameters :form :user]))]
                                       [:p [:a {:href (:href (yada/uri-for ctx ::secret))} "secret"]]
                                       [:p [:a {:href (:href (yada/uri-for ctx ::logout))} "logout"]]
                                       )))
                       (assoc (:response ctx)
                              ;; It's possible the user was already logged in, in which case we log them out
                              :cookies {"session" {:value "" :expires 0}}
                              :body (html [:h1 "Login failed"]
                                          [:p [:a {:href (:uri (yada/uri-for ctx ::login))} "try again"]]
                                          [:p [:a {:href (:uri (yada/uri-for ctx ::secret))} "secret"]])))))

       :consumes "application/x-www-form-urlencoded"
       :produces "text/html"}}})))

(defn build-routes []
  (try
    ["/security"
     [
      ["" (redirect ::index)]
      ["/" (redirect ::index)]

      ["/index.html"
       (-> (new-template-resource
            "templates/page.html"
            (fn [ctx]
              {:homeref (:href (yada/uri-for ctx :yada.dev.docsite/index))
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
                  [:li [:a {:href (:href (yada/uri-for ctx ::basic-example))} "Basic"]]
                  [:li [:a {:href (:href (yada/uri-for ctx ::login))} "Session"]]
                  [:li [:a {:href (:href (yada/uri-for ctx ::bearer-example))} "Bearer (OAuth2)"]]]

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
                {:id ::basic-example
                 :access-control
                 {:scheme "Basic"
                  :verify (fn [[user password]]
                            (when (= [user password] ["scott" "tiger"])
                              {:user "scott"
                               :roles #{"secret/view"}}))
                  :authorization {:methods {:get "secret/view"}}}})))]

      ["/cookie"

       (let [secret "9eLPqOKtc3wiJImA69ybMXGVjnHMbZM9+pXs"]

         {"/login.html"
          (login
           [{:label "User" :type :text :name "user"}
            {:label "Password" :type :password :name "password"}]
           secret)

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
                                          [:p [:a {:href (:href (yada/uri-for ctx ::login))} "login"]]))))}}}))

          "/secret.html"
          (yada
           (resource
            {:id ::secret

             :methods {:get {:response (fn [ctx]
                                         (html
                                          [:h1 "Seek happiness"]
                                          [:p [:a {:href (:href (yada/uri-for ctx ::logout))} "logout"]]
                                          ))
                             :produces "text/html"}}

             :access-control
             {:authentication-schemes
              [{:scheme :jwt
                ;; TODO: Don't expose secrets in resource models
                :yada.jwt/secret secret}]
              :authorization {:methods {:get [:or
                                              "secret/view"
                                              "accounts/view"]}}}

             :responses {401 {:produces "text/html" ;; TODO: If we neglect to put in produces we get an error
                              :response (fn [ctx]
                                          (html
                                           [:h1 "Sorry"]
                                           [:p "You are not authorized yet"]
                                           [:p "Please " [:a {:href (:href (yada/uri-for ctx ::login))} "login" ]]
                                           ))}
                         403 {:produces "text/html" ;; TODO: If we neglect to put in produces we get an error
                              :response (fn [ctx]
                                          (html
                                           [:h1 "Sorry"]
                                           [:p "Your access is forbidden"]
                                           [:p "Try another user? " [:a {:href (:href (yada/uri-for ctx ::logout))} "logout" ]]
                                           ))}}}))})]

      ["/bearer"
       (let [github-client-id "41cd4ede085de5969bd4"
             google-client-id "848581248307-th1bhe45q00alks9ricm8mj4kja4nn7v.apps.googleusercontent.com"
             secret (hash/sha256 "ABCD1234")]
         [
          ["/login"
           (-> (new-template-resource
                "templates/page.html"
                (fn [ctx]
                  {:homeref (:href (yada/uri-for ctx :yada.dev.docsite/index))
                   :content
                   (html
                    [:div.container
                     [:h2 "Bearer (OAuth2) example"]
                     [:p
                      [:form
                       {:action (:href (yada/uri-for ctx ::initiate-google))
                        :method :post}
                       [:input {:type :submit :value "Login via Google"}]]]
                     [:p
                      [:form
                       {:action (:href (yada/uri-for ctx ::initiate-github))
                        :method :post}
                       [:input {:type :submit :value "Login via GitHub"}]]]])}))
               (assoc :id ::bearer-example))]

          ["/initiate-google" (oauth/oauth2-initiate-resource
                               {:id ::initiate-google
                                :type :google
                                :client-id google-client-id
                                :redirect-uri ::google-oauth-callback
                                :secret secret
                                :target-uri ::welcome
                                :authorization-uri "https://accounts.google.com/o/oauth2/v2/auth"
                                :scope "openid email profile"
                                })]

          ["/initiate-github" (oauth/oauth2-initiate-resource
                               {:id ::initiate-github
                                :type :github
                                :client-id github-client-id
                                :redirect-uri ::github-oauth-callback
                                :secret secret
                                :target-uri ::welcome
                                :authorization-uri "https://github.com/login/oauth/authorize"
                                :scope (str/join "," ["user:email" "read:gpg_key"])
                                })]

          ["/callback-github"
           (oauth/oauth2-callback-resource-github
            {:id ::github-oauth-callback
             :client-id github-client-id
             :client-secret "5e4dbc37ce2e323fb2df7fcfb2de39b65f0c82b3"
             :user-agent "yada"
             :secret secret
             :access-token-url "https://github.com/login/oauth/access_token"
             :access-token-handler
             (fn [access-token]
               (let [req-opts {:headers {"user-agent" "yada-security-example"}
                               :query-params {"access_token" access-token}}]
                 (d/chain
                  (http/get "https://api.github.com/user/emails" req-opts)

                  (fn [response]
                    (if-not (= (:status response) 200)
                      (d/error-deferred (ex-info "Didn't get 200 from /user/emails call" {}))
                      (json/parse-stream (io/reader (:body response)))))

                  (fn [body]
                    (let [uid (->> body
                                   (filter #(get % "verified"))
                                   (map #(get % "email"))
                                   ;;(filter #(re-matches #".*@juxt.pro" %))
                                   first)]
                      (when uid

                        ;; Get more details
                        ;; We'll need emails in scope
                        (d/chain
                         (http/get "https://api.github.com/user" req-opts)

                         (fn [response]
                           (if-not (= (:status response) 200)
                             (d/error-deferred (ex-info "Didn't get 200 from /user call" {}))
                             (json/parse-stream (io/reader (:body response)))))

                         (fn [body]
                           {:id uid
                            :name (get body "name")}

                           ))))))))})]

          ["/callback-google"
           (oauth/oauth2-callback-resource-google
            {:id ::google-oauth-callback
             :client-id google-client-id
             :client-secret "RAIjto_srMTFpo3xIdEbyY22"
             :secret secret
             :redirect-uri ::google-oauth-callback
             :access-token-url "https://www.googleapis.com/oauth2/v4/token"
             :handler
             (fn [{:keys [access-token openid-claims]}]
               {:name (get openid-claims "name")
                :email (get openid-claims "email")})})]

          ["/welcome"
           (resource
            {:id ::welcome

             :access-control
             {:authentication-schemes [{:scheme :oauth2 :yada.oauth2/secret secret}]}

             :methods
             {:get {:produces "text/html"
                    :response (fn [ctx]
                                (let [data (-> ctx :authentication (get "default"))]
                                  (html
                                   [:h1 "Welcome " (get data :name) "!"]
                                   [:pre (pr-str data)])))}}})]])]]]

    (catch Throwable e
      (errorf e "Getting exception on security example routes")
      ["/security/cookie/secret.html" (yada (str e))])))

(s/defrecord SecurityExamples []
  RouteProvider
  (routes [_] (build-routes)))

(defn new-security-examples []
  (map->SecurityExamples {}))
