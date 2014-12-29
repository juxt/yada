(ns yada.dev.website
  (:require
   [com.stuartsierra.component :as component]
   [clojure.pprint :refer (pprint)]
   [modular.ring :refer (WebRequestHandler)]
   [manifold.deferred :as d]
   [bidi.bidi :refer (match-route)]
   [yada.core :refer (make-handler-from-swagger-resource)]
   [yada.dev.database :refer (find-pets find-pet-by-id)]
   [pets :refer (pets-spec)]
   [schema.core :as s]
   ))

;;(match-route pets-spec "/pets/123")

(defrecord Website [database]
  WebRequestHandler
  (request-handler [this]
    (fn [req]
      (if-let [matched (match-route pets-spec (:uri req))]
        (let [handler
              (make-handler-from-swagger-resource
               (:handler matched)
               (case (get-in (:handler matched) [(:request-method req) :operationId])
                 :findPets {:model (fn [_] (find-pets database))}
                 :addPet {:body "addPet"}
                 :findPetById {:model (fn [{:keys [params]}] (find-pet-by-id database (get params :id)))}
                 {}))]
          (handler (-> req
                     (update-in [:params] merge (:route-params matched))
                     (update-in [:route-params] merge (:route-params matched))
                     )))
        {:status 404
         :headers {"content-type" "text/plain"}
         :body "No resource found"}))))

(def new-website-schema {})

(defn new-website [& {:as opts}]
  (component/using
   (->> opts
     (merge {})
     (s/validate new-website-schema)
     map->Website)
   [:database]))
