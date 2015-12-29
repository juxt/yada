;; Copyright © 2015, JUXT LTD.

(ns phonebook.resources
  (:require
   [bidi.bidi :refer [path-for]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [schema.core :as s]
   [phonebook.db :as db]
   [phonebook.html :as html]
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.resource :refer [resource]]
   [yada.yada :as yada])
  (:import [manifold.stream.core IEventSource]))

;; A custom scheme (indicated by the absence of a :scheme entry).
;; You can plugin your own authenticator here, with full access to yada's request context.
;; This authenticator is just a simple example to allow the Swagger UI to access the phonebook.

;; Access to our phonebook is public, but if we want to modify it we
;; must have sufficient authorization. This is what this access
;; control definition does.

;; We must be very careful not to allow a rogue script on another
;; website to hijack our cookies and destroy our phonebook!.
(def access-control
  {:authentication
   {:realms
    {"phonebook"
     {:schemes
      [{:scheme "Basic"
        :authenticator {["tom" "watson"] "tom@ibm.com"}}
       {:authenticator
        (fn [ctx]
          (cond
            (= (get-in ctx [:request :headers "api_key"]) "masterkey") "swagger"))}]}}}

   :allow-origin #{"http://localhost:8090"
                   "https://yada.juxt.pro"}

   :allow-methods #{:get :post :put :delete}
   
   :allow-headers ["api_key"]})

(defn new-index-resource [db *routes]
  (resource
   {:produces [{:media-type
                #{"text/html" "application/edn;q=0.9" "application/json;q=0.8"}
                :charset "UTF-8"}]
    
    :methods
    {:get {:parameters {:query {(s/optional-key :q) String}}
           :response (fn [ctx]
                       (let [q (get-in ctx [:parameters :query :q])
                             entries (if q
                                       (db/search-entries db q)
                                       (db/get-entries db))]
                         (case (yada/content-type ctx)
                           "text/html" (html/index-html entries @*routes q)
                           entries)))}

     :post {:parameters {:form {:surname String :firstname String :phone String}}
            :consumes [{:media-type
                        #{"application/x-www-form-urlencoded"}
                        :charset "UTF-8"}]

            :response (fn [ctx]
                        (let [id (db/add-entry db (get-in ctx [:parameters :form]))]
                          (java.net.URI. nil nil (path-for @*routes :phonebook.api/entry :entry id) nil)))}}

    :access-control access-control}))

(defn new-entry-resource [db *routes]
  (resource
   {:parameters {:path {:entry Long}}
    :produces [{:media-type #{"text/html"
                              "application/edn;q=0.9"
                              "application/json;q=0.8"}
                :charset "UTF-8"}]
    :methods
    {:get
     {:response
      (fn [ctx]
        
        (let [id (get-in ctx [:parameters :path :entry])
              {:keys [firstname surname phone] :as entry} (db/get-entry db id)]
          (when entry
            (case (yada/content-type ctx)
              "text/html"
              (html/entry-html
               entry
               {:entry (path-for @*routes :phonebook.api/entry :entry id)
                :index (path-for @*routes :phonebook.api/index)})
              entry))))}

     :put
     {:parameters
      {:form {:surname String
              :firstname String
              :phone String}}
      :consumes
      [{:media-type #{"multipart/form-data"
                      "application/x-www-form-urlencoded"}}]
      :response
      (fn [ctx]
        (let [entry (get-in ctx [:parameters :path :entry])
              form (get-in ctx [:parameters :form])]
          (assert entry)
          (assert form)
          (db/update-entry db entry form)))}

     :delete
     {:response
      (fn [ctx]
        (let [id (get-in ctx [:parameters :path :entry])]
          (db/delete-entry db id)
          ;; nil body
          nil))}}

    :access-control access-control}))
