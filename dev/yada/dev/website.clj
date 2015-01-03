;; Copyright © 2015, JUXT LTD.

(ns yada.dev.website
  (:require
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler request-handler)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]
   bidi.swagger
   [bidi.ring :as br]
   [hiccup.core :refer (html)]
   pets
   [cheshire.core :as json]
   clojure.pprint
   clojure.string
   ))

(defmulti swagger-spec (fn [version spec] version))

(defmethod swagger-spec "1.2"
  [version spec]
  {:apiVersion "1.0.0"
   :swaggerVersion "1.2"
   :apis (for [[path obj] (bidi.swagger/swagger-paths spec)]
           {:path path
            :description "foo"
            :operations (for [[k v] obj]
                          {:method (clojure.string/upper-case (name k))
                           :summary "bar"}
                          )}
           )}
  )

(defrecord Website []
  WebService
  (request-handlers [this]
    {:index (fn [req] {:status 200 :body "SWAGGER-UI"})
     :spec-html (fn [req]
                  {:status 200
                   :headers {"content-type" "text/html"}
                   :body (html [:pre (with-out-str
                                       (clojure.pprint/pprint
                                        (swagger-spec "1.2" pets/pets-spec)))])})
     :spec-json-old (fn [req]
                  {:status 200
                   :headers {"content-type" "application/json"}
                   :body (json/encode (swagger-spec "1.2" pets/pets-spec))})})
  (routes [this] ["" {"/index" :index
                      "/spec.html" :spec-html
                      "/spec.json" :spec-json
                      "/swag" (br/files {:dir "/home/malcolm/src/swagger-ui/dist"})
                      }])
  (uri-context [this] ""))

(def new-website-schema {})

(defn new-website [& {:as opts}]
  (component/using
   (->> opts
     (merge {})
     (s/validate new-website-schema)
     map->Website)
   []))
