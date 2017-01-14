;; Copyright © 2014-2017, JUXT LTD.

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
   #_[yada.dev.docsite :refer [new-docsite]]
   #_[yada.dev.talks :refer [new-talks]]
   #_[yada.dev.database :refer [new-database]]
   #_[yada.dev.server :refer [new-webserver]]
   #_[yada.dev.swagger :refer [new-phonebook-swagger-ui-index]]
   [yada.dev.config :as config]
   #_[yada.dev.hello :refer [new-hello-world-example]]
   #_[yada.dev.security :refer [new-security-examples]]
   #_[yada.dev.upload :refer [new-upload-examples]]
   #_[yada.dev.async :refer [new-sse-example]]
   #_[yada.dev.error-example :refer [new-error-example]]

   [yada.dev.web-server :refer [new-web-server]]
   )
  )


#_(defn database-components [system]
  (assoc system ::database (new-database)))

#_(defn docsite-components [system config]
  (assoc
   system
   #_::docsite (new-docsite :config config)

   #_::security-examples (new-security-examples)
   #_::sse-example (new-sse-example)
   #_::upload-examples (new-upload-examples)

   ;; TODO: Replace new-web-resources with a yada equivalent
   #_::jquery #_(new-web-resources
                 :key :jquery
                 :uri-context "/jquery"
                 :resource-prefix "META-INF/resources/webjars/jquery/2.1.3")

   #_::bootstrap #_(new-web-resources
                :key :bootstrap
                :uri-context "/bootstrap"
                :resource-prefix "META-INF/resources/webjars/bootstrap/3.3.6")

   #_::web-resources #_(new-web-resources
                    :uri-context "/static"
                    :resource-prefix "static")

   #_::highlight-js-resources
   #_(new-archived-web-resources :archive (io/resource "highlight.zip") :uri-context "/hljs/")
   ))

#_(defn swagger-ui-components [system]
  (assoc system
         ::phonebook-swagger-ui-index
         (new-phonebook-swagger-ui-index)
         ::swagger-ui
         (new-web-resources
          :key :swagger-ui
          :uri-context "/swagger-ui"
          :resource-prefix "META-INF/resources/webjars/swagger-ui/2.2.6")))

#_(defn http-server-components [system config]
  (assoc system
         ::docsite-server
         (new-webserver
          {:port (-> config :docsite :port)
           :raw-stream? true}
          (-> config :docsite :vhosts))
         ::docsite-router (new-router)))

#_(defn hello-world-components [system config]
  (assoc system ::hello-world (new-hello-world-example config)))

#_(defn error-components [system]
  (assoc system ::error-example (new-error-example)))

#_(defn talks-components [system config]
  (assoc system ::talks (new-talks config)))

#_(defn phonebook-components [system config]
  (assoc system ::phonebook (new-phonebook (:phonebook config))))

(defn new-system-map
  [config]
  (system-map
   :web-server (new-web-server config)
   )

  #_(apply system-map
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
  {}
  #_{::docsite-server {:router ::docsite-router
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
