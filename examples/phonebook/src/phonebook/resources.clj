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
   [yada.yada :as yada]))

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
    ;; :form should be :body
    {:parameters {:post {:form {:surname String
                                :firstname String
                                :phone String}}}
     :representations representations})

  m/Post
  (POST [_ ctx]
    (let [id (db/add-entry db (get-in ctx [:parameters :form]))]
      (yada/redirect-after-post
       ctx (path-for @*routes :phonebook.api/entry :entry id))))

  m/Get
  (GET [_ ctx]
    (let [entries (db/get-entries db)]
      (case (yada/content-type ctx)
        "text/html" (html/index-html entries @*routes)
        entries))))

(defn new-index-resource [db *routes]
  (->IndexResource db *routes))

(defrecord EntryResource [db *routes]
  p/Properties
  (properties [_]
    {:parameters
     {:get {:path {:entry Long}}
      :post {:path {:entry Long}
             :form {(s/required-key "method") String}}
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
    (infof "Put!"))

  m/Delete
  (DELETE [_ ctx]
    (let [id (get-in ctx [:parameters :path :entry])]
      (dosync
       (alter (:phonebook db) dissoc id)
       ;; Body
       nil))))

(defn new-entry-resource [db *routes]
  (->EntryResource db *routes))
