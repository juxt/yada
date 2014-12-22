(ns ecto.core
  (:require
   [manifold.deferred :as d]
   [bidi.bidi :as bidi]
   [ring.mock.request :as mock]))

;; API specs. are created like this

;; This is kind of like a bidi route structure

;; But we shouldn't limit ourselves to only that which is declared, because so much more can be generated, like 404s, etc.

;; "It is better to have 100 functions operate on one data structure than 10 functions on 10 data structures." —Alan Perlis

(def api
  {:swagger "2.0"
   :info {:version "0.0.0"
          :title "Foobar"}
   :paths
   [["/persons" {:get {:description "Gets 'Person' objects"
                       :parameters [{:name "size"
                                     :in :query
                                     :description "Size of array"
                                     :required true
                                     :type :number
                                     :format :double}]
                       :produces ["text/html" "application/json" "text/plain"]
                       :responses {404 {:description "Not found"}
                                   200 {:description "Successful response"
                                        :schema {:title "ArrayOfPersons"
                                                 :type :array
                                                 :properties
                                                 {"name" {:type :string}
                                                  "single" :boolean}
                                                 }}}}}]]})

;; Aha - bidi is recursive, swagger isn't?

;; Keep bidi routes to keys, keys should reference swagger objects
;; There should be a generic handler to handle a swagger object

;;(bidi/match-route ["" (:paths api)] "/persons")

;; From this a bidi structure could be generated to target the handler.

(defn api-handler []
  (fn [req]
    #_{:status 200 :body "index"}
    (d/let-flow
       [status (future 200)]
       {:status status :body "foo"})))

;; There should be a general handler that does the right thing
;; wrt. available methods (Method Not Allowed) and calls out to
;; callbacks accordingly. Perhaps there's no sense in EVERYTHING being
;; overridable, as with Liberator. It should hard-code the things that
;; don't make sense to override, and use hooks for the rest.

;; Resource existence is most important - and not covered by swagger, so it's a key hook.

;; Return deferreds, if necessary, if the computation is length to compute (e.g. for exists? checks)

;; CORS support: build this in, make allow-origin first-class, which headers should be allowed should be a hook (with default)
