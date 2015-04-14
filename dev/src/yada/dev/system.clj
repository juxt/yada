;; Copyright © 2015, JUXT LTD.

(ns yada.dev.system
  "Components and their dependency relationships"
  (:refer-clojure :exclude (read))
  (:require
   [clojure.java.io :as io]
   [clojure.tools.reader :refer (read)]
   [clojure.string :as str]
   [clojure.tools.reader.reader-types :refer (indexing-push-back-reader)]
   [com.stuartsierra.component :refer (system-map system-using using)]
   [modular.maker :refer (make)]
   [modular.bidi :refer (new-router new-web-resources new-archived-web-resources new-redirect)]
   [modular.clostache :refer (new-clostache-templater)]
   [yada.dev.website :refer (new-website)]
   [yada.dev.pets :refer (new-pets-api-service)]
   [yada.dev.user-guide :refer (new-user-guide)]
   [yada.dev.database :refer (new-database)]
   [modular.aleph :refer (new-webserver)]
   [modular.component.co-dependency :refer (co-using system-co-using)]

   [yada.dev.async :refer (new-handler)]
   [yada.dev.external :refer (new-external-resources)]))

(defn ^:private read-file
  [f]
  (read
   ;; This indexing-push-back-reader gives better information if the
   ;; file is misconfigured.
   (indexing-push-back-reader
    (java.io.PushbackReader. (io/reader f)))))

(defn ^:private config-from
  [f]
  (if (.exists f)
    (read-file f)
    {}))

(defn ^:private user-config
  []
  (config-from (io/file (System/getProperty "user.home") ".yada.edn")))

(defn ^:private config-from-classpath
  []
  (if-let [res (io/resource "yada.edn")]
    (config-from (io/file res))
    {}))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  []
  (merge (config-from-classpath)
         (user-config)))

(defn database-components [system config]
  (assoc system
    :database
    (->
      (make new-database config)
      (using []))))

(defn api-components [system config]
  (assoc system
    :pets-api
    (->
      (make new-pets-api-service config)
      (using {:database :database}))))

(defn website-components [system config]
  (assoc
   system
   :clostache-templater (make new-clostache-templater config)
   :user-guide (make new-user-guide config)
   :website (make new-website config)
   :jquery (make new-web-resources config
                 :key :jquery
                 :uri-context "/jquery"
                 :resource-prefix "META-INF/resources/webjars/jquery/2.1.3")
   :bootstrap (make new-web-resources config
                    :key :bootstrap
                    :uri-context "/bootstrap"
                    :resource-prefix "META-INF/resources/webjars/bootstrap/3.3.2")
   :web-resources (make new-web-resources config
                        :uri-context "/static"
                        :resource-prefix "public")
   :highlight-js-resources
    (make new-archived-web-resources config :archive (io/resource "highlight.zip") :uri-context "/hljs/")
   ))

(defn swagger-ui-components [system config]
  (assoc system
         :swagger-ui
         (make new-web-resources config
               :key :swagger-ui
               :uri-context "/swagger-ui"
               :resource-prefix "META-INF/resources/webjars/swagger-ui/2.1.0-alpha.6")))

(defn router-components [system config]
  (assoc system
    :router
    (make new-router config)))

(defn http-server-components [system config]
  (assoc system
    :http-server
    (make new-webserver config
          :port 8080)
    ))

(defn external-server-components [system config]
  (assoc
   system
   :external-resources (new-external-resources)
   :external-router (using
                     (make new-router config)
                     [:external-resources])
   :external-server (using
                     (new-webserver :port 8081)
                     {:request-handler :external-router
                      :user-guide :user-guide})))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
        (database-components config)
        (api-components config)
        (website-components config)
        (swagger-ui-components config)
        (router-components config)
        (http-server-components config)
        (external-server-components config)
        (assoc :redirect (new-redirect :from "/" :to :yada.dev.website/index))
        ))))

(defn new-dependency-map
  []
  {:http-server {:request-handler :router}
   :external-server {:request-handler :external-router}
   :user-guide {:templater :clostache-templater}
   :router [:pets-api
            :user-guide
            :swagger-ui
            :website
            :jquery :bootstrap
            :web-resources
            :highlight-js-resources
            :redirect]
   :website {:pets-api :pets-api
             :templater :clostache-templater}})

(defn new-co-dependency-map
  []
  {:website {:router :router}
   :user-guide {:router :router}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config))
      (system-using (new-dependency-map))
      (system-co-using (new-co-dependency-map))))
