;; Copyright © 2015, JUXT LTD.

(ns yada.dev.phonebook
  (:require
   [bidi.bidi :refer [path-for]]
   [clojure.tools.logging :refer :all]
   [schema.core :as s]
   [yada.protocols :as p]
   [yada.methods :as m]
   [yada.yada :refer [yada]]))

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

(defrecord Phonebook [db *routes]
  p/Properties
  (properties [_] {:parameters {:post {:form {(s/required-key "surname") String
                                              (s/required-key "firstname") String
                                              (s/required-key "phone") String}}}
                   :representations
                   [{:media-type #{"application/json" "application/edn"}}]})
  (properties [_ ctx] {})
  m/Get (GET [_ ctx] @(:phonebook db))
  m/Post (POST [_ ctx]
           (if-let [rec (get-in ctx [:parameters :form])]
             (dosync
              (ensure (:next-entry db))
              (let [nextval @(:next-entry db)]
                (alter (:phonebook db) conj [nextval rec])
                (alter (:next-entry db) inc)
                (-> (:response ctx)
                    (assoc :status 303)
                    (update-in [:headers] merge {"location" (path-for @*routes ::phonebook-entry :entry nextval)}))))

             (do
               (infof "parameters is %s" (:parameters ctx))
               (throw (ex-info "No record found (delete me)" {})))
             )))


(defrecord PhonebookEntry [db *routes]
  p/Properties)

(defn phonebook [db *routes]
  ["/phonebook" {"" (yada (->Phonebook db *routes) {:id ::phonebook})
                 ["/" :entry] (yada (->PhonebookEntry db *routes)
                                    {:id ::phonebook-entry})}])
