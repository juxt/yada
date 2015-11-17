;; Copyright © 2015, JUXT LTD.

(ns phonebook.www
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
   [yada.yada :as yada])
  (:import [manifold.stream.core IEventSource]))

(def representations
  [{:media-type #{"text/html"
                  "application/edn;q=0.9"
                  "application/json;q=0.8"}
    ;; TODO: When we remove this, we get non UTF-8 encoding, find out why
    :charset "UTF-8"
    }])

(defrecord IndexResource [db *routes]
  p/Properties
  (properties [_]
    {:parameters {:get {:query {(s/optional-key :q) String}}
                  :post {:form {:surname String :firstname String :phone String}}}
     :representations representations})

  m/Post
  (POST [_ ctx]
    (let [id (db/add-entry db (get-in ctx [:parameters :form]))]
      (yada/redirect-after-post
       ctx (path-for @*routes :phonebook.api/entry :entry id))))

  m/Get
  (GET [_ ctx]
    (let [q (get-in ctx [:parameters :query :q])
          entries (if q
                    (db/search-entries db q)
                    (db/get-entries db))]
      (case (yada/content-type ctx)
        "text/html" (html/index-html entries @*routes q)
        entries))))

(defn new-index-resource [db *routes]
  (->IndexResource db *routes))

(defrecord EntryResource [db *routes]
  p/Properties
  (properties [_]
    {:parameters
     {:get {:path {:entry Long}}
      :put {:path {:entry Long}
            ;; Should like to make this a form again
            :body {:surname String
                   :firstname String
                   :phone String}}

      :delete {:path {:entry Long}}}
     :representations representations})

  m/Get
  (GET [_ ctx]
    (let [id (get-in ctx [:parameters :path :entry])
          {:keys [firstname surname phone] :as entry} (db/get-entry db id)]
      (when entry
        (case (yada/content-type ctx)
          "text/html"
          (html/entry-html
           entry
           {:entry (path-for @*routes :phonebook.api/entry :entry id)
            :index (path-for @*routes :phonebook.api/index)})
          entry))))

  m/Put
  (PUT [_ ctx]
    (let [entry (get-in ctx [:parameters :path :entry])
          body (get-in ctx [:parameters :body])]
      (db/update-entry db entry body)))

  m/Delete
  (DELETE [_ ctx]
    (let [id (get-in ctx [:parameters :path :entry])]
      (db/delete-entry db id)
      nil ; body
      )))

(defn new-entry-resource [db *routes]
  (->EntryResource db *routes))
