;; Copyright © 2015, JUXT LTD.

(ns yada.dev.phonebook
  (:require
   [bidi.bidi :refer [path-for RouteProvider]]
   [clojure.tools.logging :refer :all]
   [clojure.java.io :as io]
   [hiccup.core :refer [html]]
   [modular.component.co-dependency :refer (co-using)]
   [schema.core :as s]
   yada.charset
   [yada.methods :as m]
   [yada.protocols :as p]
   [yada.representation :as rep] ; remove
   [yada.yada :refer [yada] :as yada]))

;; Create a simple HTTP service to represent a phone book.

;; Acceptance criteria.
;; - List all entries in the phone book.
;; - Create a new entry to the phone book.
;; - Remove an existing entry in the phone book.
;; - Update an existing entry in the phone book.
;; - Search for entries in the phone book by surname.

;; A phone book entry must contain the following details:
;; - Surname
;; - Firstname
;; - Phone number
;; - Address (optional)

;; The solution can be in any language. Please upload your project to github and provide us with the URL.
;; We are not looking for a client or UI for this solution, a simple HTTP based service will suffice.

(defn index-html [entries routes]
  (html
   [:body
    (if (not-empty entries)
      [:table
       [:thead
        [:tr
         [:th "Entry"]
         [:th "Surname"]
         [:th "Firstname"]
         [:th "Phone"]]]

       [:tbody
        (for [[id {:keys [surname firstname phone]}] entries
              :let [href (path-for routes ::phonebook-entry :entry id)]]
          [:tr
           [:td [:a {:href href} href]]
           [:td surname]
           [:td firstname]
           [:td phone]])]]
      [:h2 "No entries"])

    [:h4 "Add entry"]

    [:form {:method :post}
     [:style "label { margin: 6pt }"]
     [:p
      [:label "Surname"]
      [:input {:name "surname" :type :text}]]
     [:p
      [:label "Firstname"]
      [:input {:name "firstname" :type :text}]]
     [:p
      [:label "Phone"]
      [:input {:name "phone" :type :text}]]
     [:p
      [:input {:type :submit :value "Add entry"}]]]]))

(defn entry-html [{:keys [firstname surname phone]}
                  {:keys [entry index]}
                  ]
  (html
   [:body
    [:script (format "index=\"%s\";" index)]
    [:script (format "entry=\"%s\";" entry)]
    [:h2 (format "%s %s" firstname surname)]
    [:p "Phone: " phone]

    [:h4 "Update entry"]
    [:form#entry
     [:style "label { margin: 6pt }"]
     [:p
      [:label "Firstname"]
      [:input {:type :text :value firstname}]
      ]
     [:p
      [:label "Surname"]
      [:input {:type :text :value surname}]]
     [:p
      [:label "Phone"]
      [:input {:type :text :value phone}]]
     ]

    [:button {:onclick (format "phonebook.update('%s')" entry)} "Update"]
    [:button {:onclick (format "phonebook.delete('%s')" entry)} "Delete"]
    [:p [:a {:href index} "Index"]]
    [:p "Warning, the Update button has not yet been fully implemented!"]
    [:script (slurp (io/resource "js/phonebook.js"))]]))

(defn add-entry
  "Add a new entry to the database"
  [db entry]
  (dosync
   (let [nextval @(:next-entry db)]
     (alter (:phonebook db) conj [nextval entry])
     (alter (:next-entry db) inc)
     nextval)))

(def phonebook-representations
  [{:media-type #{"text/html"
                  "application/edn;q=0.9"
                  "application/json;q=0.8"}
    ;; TODO: When we remove this, we get non UTF-8 encoding, find out why
    :charset "UTF-8"
    }])

(defrecord Phonebook [db *router]
  p/Properties
  (properties [_]
    {:parameters {:post {:form {(s/required-key "surname") String
                                (s/required-key "firstname") String
                                (s/required-key "phone") String}}}
     :representations phonebook-representations})

  m/Get
  (GET [_ ctx]
    (let [entries @(:phonebook db)]
      (case (yada/content-type ctx)
        "text/html" (index-html entries (:routes @*router))
        entries)))

  m/Post
  (POST [_ ctx]
    (dosync
     (let [{:strs [surname firstname phone]} (get-in ctx [:parameters :form])
           nextval (add-entry db {:surname surname
                                  :firstname firstname
                                  :phone phone})]

       (yada/redirect-after-post
        ctx (path-for (:routes @*router) ::phonebook-entry :entry nextval))))))

(defrecord PhonebookEntry [db *router]
  p/Properties
  (properties [_]
    {:parameters
     {:get {:path {:entry Long}}
      :post {:path {:entry Long}
             :form {(s/required-key "method") String}}
      :delete {:path {:entry Long}}}
     :representations phonebook-representations})

  m/Get
  (GET [_ ctx]
    (let [id (get-in ctx [:parameters :path :entry])
          {:keys [firstname surname phone] :as entry} (get @(:phonebook db) id)]
      (when entry
        (case (yada/content-type ctx)
          "text/html"
          (entry-html
           entry
           {:entry (path-for (:routes @*router) ::phonebook-entry :entry id)
            :index (path-for (:routes @*router) ::phonebook)})
          entry))))

  m/Put
  (PUT [_ ctx]
    (infof "Put!")
    )

  m/Delete
  (DELETE [_ ctx]
    (let [id (get-in ctx [:parameters :path :entry])]
      (dosync
       (alter (:phonebook db) dissoc id)
       ;; Body
       nil))))

(defn phonebook-api [db *router]
  ["/phonebook" {"" (yada (->Phonebook db *router) {:id ::phonebook})
                 ["/" :entry] (yada (->PhonebookEntry db *router)
                                    {:id ::phonebook-entry})}])

(defn create-db [entries]
  {:phonebook (ref entries) :next-entry (ref (inc (count entries)))})

(defrecord PhonebookExample [*router]
  RouteProvider
  (routes [_]
    (phonebook-api
     (create-db {1 {:surname "Sparks"
                    :firstname "Malcolm"
                    :phone "1234"}

                 2 {:surname "Pither"
                    :firstname "Jon"
                    :phone "1235"}}) *router)))

(defn new-phonebook-example [config]
  (co-using
   (map->PhonebookExample {})
   [:router]))
