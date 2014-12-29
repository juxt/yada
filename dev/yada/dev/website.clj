(ns yada.dev.website
  (:require
   [com.stuartsierra.component :as component]
   [clojure.pprint :refer (pprint)]
   [modular.ring :refer (WebRequestHandler)]
   [manifold.deferred :as d]
   [bidi.bidi :refer (match-route)]
   [yada.core :refer (make-handler-from-swagger-resource)]
   [yada.dev.database :refer (find-pets)]
   [pets :refer (pets-spec)]
   [schema.core :as s]
   ))

;;(match-route pets-spec "/pets/123")

(defrecord Website [database]
  WebRequestHandler
  (request-handler [this]
    (fn [req]
      (if-let [resource (:handler (match-route pets-spec (:uri req)))]
        (let [handler
              (make-handler-from-swagger-resource
               resource
               (let [opts
                     (case (get-in resource [(:request-method req) :operationId])
                       :findPets {:body (fn [_] (pr-str (find-pets database)))}
                       :addPet {:body "addPet"}
                       :findPetById {:body "findPetById"}
                       {})]
                 opts
                 ))]
          (handler req))
        {:status 400
         :headers {"content-type" "text/plain"}
         :body "Nothing"})
      )))

(def new-website-schema {})

(defn new-website [& {:as opts}]
  (component/using
   (->> opts
     (merge {})
     (s/validate new-website-schema)
     map->Website)
   [:database]))
