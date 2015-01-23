;; Copyright © 2015, JUXT LTD.

(ns yada.dev.website
  (:require
   [com.stuartsierra.component :as component]
   [modular.ring :refer (WebRequestHandler request-handler)]
   [modular.bidi :refer (WebService)]
   [schema.core :as s]
   yada.swagger
   [clojure.java.io :as io]
   [bidi.ring :as br]
   [hiccup.core :refer (html)]
   [cheshire.core :as json]
   clojure.pprint
   clojure.string
   ))

(defmulti swagger-spec (fn [version spec] version))

(defmethod swagger-spec "1.2"
  [version spec]
  {:info {:title "Demo"
          :description "A demo of yada"}
   :swaggerVersion "1.2"
;;   :basePath "http://localhost:8080/pet"
   :apiVersion "1.0.0"
   :apis (for [[path obj] (yada.swagger/swagger-paths spec)]
           {:path path
            :description "foo"
            #_:operations #_(for [[k v] obj]
                          {:method (clojure.string/upper-case (name k))
                           :summary "bar"}
                          )}
           )}
  )

(defrecord Website []
  WebService
  (request-handlers [this]
    {:index (fn [req] {:status 200 :body "SWAGGER-UI"})
     #_:spec-html #_(fn [req]
                  {:status 200
                   :headers {"content-type" "text/html"}
                   :body (html [:pre (with-out-str
                                       (clojure.pprint/pprint
                                        (swagger-spec "1.2" pets/pets-spec)))])})
     #_:spec-json #_(fn [req]
                  {:status 200
                   :headers {"content-type" "application/json"}
                   :body (-> (swagger-spec "1.2" pets/pets-spec)

                           (json/encode {:pretty true}))})

     :original (fn [req]
                 (let [path (-> req :route-params :path)
                       ]
                   (if-let [res (io/resource (format "%s.json" path))]
                     {:status 200
                      :headers {"content-type" "application/json"}
                      :body
                      (json/encode
                       (json/decode
                        (slurp res))
                       {:pretty true})}
                     {:status 404
                      :body "io/resource not found"}

                     )))
     })
  (routes [this] ["" {"/index" :index
                      "/spec.html" :spec-html
                      "/spec.json" :spec-json

                      "/swag" (br/files {:dir "/home/malcolm/src/swagger-ui/dist"})
                      ["/original/" :path] :original
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
