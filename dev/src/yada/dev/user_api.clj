;; Copyright © 2015, JUXT LTD.

(ns yada.dev.user-api
  (:require
   [bidi.bidi :refer [RouteProvider tag]]
   [bidi.ring :refer [make-handler]]
   [cheshire.core :refer [decode]]
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :refer [Lifecycle]]
   [ring.mock.request :refer [request]]
   [schema.core :as s]
   [yada.charset :as charset]
   [yada.protocols :as p]
   [yada.swagger :refer [swaggered]]
   [yada.yada :as yada :refer [yada]]))

(defrecord VerboseUserApi []
  Lifecycle
  (start [component]
    (assoc component
           :db {:users
                {"alice" {:email "alice@example.org"}
                 "bob" {:email "bob@example.org"}
                 }}))
  (stop [component] component)

  RouteProvider
  (routes [{:keys [db]}]
    ["/api"
     (->
      (swaggered
       {:info {:title "User API"
               :version "0.0.1"
               :description "Example user API"}
        :basePath "/api"}
       ["" {"/users"
            {""
             (yada
              (:users db)
              {:swagger {:get {:summary "Get users"
                               :description "Get a list of all known users"}}})

             ["/" :username]
             {"" (yada/yada
                  (fn [ctx]
                    (when-let [user (get (:users db)
                                         (-> ctx :parameters :username))]
                      {:user user}))
                  {:swagger {:get {:summary "Get user"
                                   :description "Get the details of a known user"
                                   :responses {200 {:description "Known user"}
                                               404 {:description "Unknown user"}}}}
                   :parameters {:get {:path {:username s/Str}}}
                   :produces [{:media-type
                               #{"application/edn" "application/json;q=0.9" "text/html;q=0.8"}
                               :charset charset/platform-charsets}]})

              "/posts" (yada/yada
                        (fn [ctx] nil)
                        {:swagger {:post {:summary "Create a new post"}}
                         :methods [:post] })}}}])
      ;; TODO: Might be able to unresolve-handler on yada's Endpoint and
      ;; not have to tag like this, that would be nice!
      (tag ::user-api))]))

(defn new-verbose-user-api []
  (->VerboseUserApi))
