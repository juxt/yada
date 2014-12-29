(ns yada.dev.website
  (:require
   [clojure.pprint :refer (pprint)]
   [modular.ring :refer (WebRequestHandler)]
   [manifold.deferred :as d]
   [bidi.bidi :refer (match-route)]
   [yada.core :refer (make-handler-from-swagger-resource)]
   [pets :refer (pets-spec)]))

;;(match-route pets-spec "/pets/123")

(defrecord Website []
  WebRequestHandler
  (request-handler [this]
    (fn [req]
      (if-let [resource (:handler (match-route pets-spec (:uri req)))]
        (let [handler
              (make-handler-from-swagger-resource
               resource
               (let [opts
                     (case (get-in resource [(:request-method req) :operationId])
                       :findPets {:body "findPets"}
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

(defn new-website []
  (->Website))
