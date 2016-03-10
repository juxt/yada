;; Copyright © 2015, JUXT LTD.

(ns yada.dev.system
  "Components and their dependency relationships"
  (:refer-clojure :exclude [read])
  (:require
   [bidi.vhosts :refer [make-handler]]
   [clojure.java.io :as io]
   [clojure.tools.reader :refer [read]]
   [clojure.string :as str]
   [clojure.tools.reader.reader-types :refer [indexing-push-back-reader]]
   [com.stuartsierra.component :refer [system-map system-using using]]
   [modular.bidi :refer [new-router new-web-resources new-archived-web-resources new-redirect]]
   [yada.dev.docsite :refer [new-docsite]]
   [yada.dev.talks :refer [new-talks]]
   [yada.dev.database :refer [new-database]]
   [yada.dev.server :refer [new-webserver]]
   [yada.dev.swagger :refer [new-phonebook-swagger-ui-index]]
   [yada.dev.config :as config]
   [yada.dev.hello :refer [new-hello-world-example]]
   [yada.dev.security :refer [new-security-examples]]
   [yada.dev.upload :refer [new-upload-examples]]
   [yada.dev.async :refer [new-sse-example]]
   [yada.dev.error-example :refer [new-error-example]]
   [phonebook.system :refer [new-phonebook]]))

(defn database-components [system]
  (assoc system ::database (new-database)))

(defn docsite-components [system config]
  (assoc
   system
   ::docsite (new-docsite :config config)

   ::security-examples (new-security-examples)
   ::sse-example (new-sse-example)
   ::upload-examples (new-upload-examples)

   ;; TODO: Replace new-web-resources with a yada equivalent
   ::jquery (new-web-resources
            :key :jquery
            :uri-context "/jquery"
            :resource-prefix "META-INF/resources/webjars/jquery/2.1.3")

   ::bootstrap (new-web-resources
               :key :bootstrap
               :uri-context "/bootstrap"
               :resource-prefix "META-INF/resources/webjars/bootstrap/3.3.6")
   
   ::web-resources (new-web-resources
                   :uri-context "/static"
                   :resource-prefix "static")
   
   ::highlight-js-resources
   (new-archived-web-resources :archive (io/resource "highlight.zip") :uri-context "/hljs/")
   ))

(defn swagger-ui-components [system]
  (assoc system
         ::phonebook-swagger-ui-index
         (new-phonebook-swagger-ui-index)
         ::swagger-ui
         (new-web-resources
          :key :swagger-ui
          :uri-context "/swagger-ui"
          :resource-prefix "META-INF/resources/webjars/swagger-ui/2.1.3")))

(defn http-server-components [system config]
  (assoc system
         ::docsite-server
         (new-webserver
          {:port 8090
           :raw-stream? true}
          [{:scheme :https :host "yada.juxt.pro"}
           {:scheme :http :host "localhost:8090"}])
         ::docsite-router (new-router)))

(defn hello-world-components [system config]
  (assoc system ::hello-world (new-hello-world-example config)))

(defn error-components [system]
  (assoc system ::error-example (new-error-example)))

(defn talks-components [system config]
  (assoc system ::talks (new-talks config)))

(defn phonebook-components [system config]
  (assoc system ::phonebook (new-phonebook (:phonebook config))))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
          (database-components)
          (docsite-components config)
          (swagger-ui-components)
          (http-server-components config)
          (hello-world-components config)
          ;;(error-components) ; Uncomment when we reinstate custom error handling
          (talks-components config)
          (phonebook-components config)

        (assoc ::docsite-redirect (new-redirect :from "/" :to :yada.dev.docsite/index))))))

(defn new-dependency-map
  []
  {::docsite-server {:router ::docsite-router
                     :phonebook ::phonebook}
   ::docsite-router [::phonebook-swagger-ui-index
                     ::swagger-ui
                     ::hello-world
                     ::sse-example
                     ::security-examples
                     ::upload-examples
                     ::docsite
                     ::talks
                     ::jquery
                     ::bootstrap
                     ::web-resources
                     ::highlight-js-resources
                     ::docsite-redirect]

   ::docsite {:phonebook ::phonebook}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config/config :prod))
      (system-using (new-dependency-map))
      ))
