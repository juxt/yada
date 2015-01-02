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
   [modular.bidi :refer (new-router new-static-resource-service)]
   ;;[yada.dev.router :refer (new-router)]
   [yada.dev.website :refer (new-website)]
   [yada.dev.api :refer (new-api-service)]
   [yada.dev.database :refer (new-database)]
   [modular.aleph :refer (new-http-server)]))

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
    :api
    (->
      (make new-api-service config)
      (using {:database :database}))))

(defn website-components [system config]
  (assoc system
    :website
    (->
      (make new-website config)
      (using {:api :api}))))

(defn swagger-ui-components [system config]
  (assoc system
         :swagger-ui
         (make new-static-resource-service config
               :uri-context "/swagger-ui"
               :resource-prefix "META-INF/resources/webjars/swagger-ui/2.0.24")))

(defn router-components [system config]
  (assoc system
    :router
    (make new-router config)))

(defn http-server-components [system config]
  (assoc system
    :http-server
    (make new-http-server config)))

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
        (http-server-components config)))))

(defn new-dependency-map
  []
  {:http-server {:request-handler :router}
   :router [:website :api :swagger-ui]})

(defn new-co-dependency-map
  []
  {})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config))
      (system-using (new-dependency-map))))
