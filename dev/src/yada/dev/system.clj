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
   [modular.stencil :refer (new-stencil-templater)]
   [yada.dev.docsite :refer (new-docsite)]
   [yada.dev.cors-demo :refer (new-cors-demo)]
   [yada.dev.user-manual :refer (new-user-manual)]
   [yada.dev.database :refer (new-database)]
   [yada.dev.user-api :refer (new-verbose-user-api)]
   [modular.aleph :refer (new-webserver)]
   [modular.component.co-dependency :refer (co-using system-co-using)]

   [yada.dev.async :refer (new-handler)]
   [yada.dev.hello :refer (new-hello-world-example)]
   [yada.dev.error-example :refer (new-error-example)]
   [aero.core :refer (read-config)]))

(defn config
  "Return a map of the static configuration used in the component
  constructors."
  [profile]
  (read-config "dev/config.edn" {:profile profile}))

(defn database-components [system config]
  (assoc system
    :database
    (->
      (make new-database config)
      (using []))))

(defn api-components [system config]
  (assoc system
    :user-api
    (make new-verbose-user-api config)))

(defn docsite-components [system config]
  (assoc
   system
   :stencil-templater (make new-stencil-templater config)
   :user-manual (make new-user-manual config
                      :prefix ""
                      :ext-prefix "")

   :docsite (make new-docsite config)
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
                        :resource-prefix "static")
   :highlight-js-resources
   (make new-archived-web-resources config :archive (io/resource "highlight.zip") :uri-context "/hljs/")

   ))

(defn swagger-ui-components [system config]
  (assoc system
         :swagger-ui
         (make new-web-resources config
               :key :swagger-ui
               :uri-context "/swagger-ui"
               :resource-prefix "META-INF/resources/webjars/swagger-ui/2.1.1")))

(defn http-server-components [system config]
  (assoc system
         :docsite-server
         (make new-webserver config
               :port 8090
               ;; raw-stream? = true gives us a manifold stream of io.netty.buffer.ByteBuf instances
               ;; Use to convert to a stream bs/to-input-stream
               :raw-stream? true)

         :docsite-router
         (make new-router config)

         :cors-demo-server
         (make new-webserver config :port 8092)
         :cors-demo-router
         (make new-router config)
         ))

(defn hello-world-components [system config]
  (assoc
   system
   :hello-world (new-hello-world-example)))

(defn error-components [system config]
  (assoc
   system
   :error-example (new-error-example)))

(defn cors-demo-components [system config]
  (assoc
   system
   :cors-demo (new-cors-demo)))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
        (database-components config)
        (api-components config)
        (docsite-components config)
        (swagger-ui-components config)
        (http-server-components config)
        (hello-world-components config)
        (error-components config)
        (cors-demo-components config)

        (assoc :docsite-redirect (new-redirect :from "/" :to :yada.dev.docsite/index))
        (assoc :cors-demo-redirect (new-redirect :from "/" :to :yada.dev.cors-demo/index))
        ))))

(defn new-dependency-map
  []
  {:docsite-server {:request-handler :docsite-router}
   :cors-demo-server {:request-handler :cors-demo-router}

   :user-manual {:templater :stencil-templater}

   :docsite-router [:swagger-ui
                    :hello-world
                    :error-example
                    :user-api
                    :user-manual
                    :docsite
                    :jquery :bootstrap
                    :web-resources
                    :highlight-js-resources
                    :docsite-redirect]

   :cors-demo-router [:cors-demo
                      :jquery :bootstrap
                      :web-resources
                      :highlight-js-resources
                      :cors-demo-redirect]

   :docsite {:templater :stencil-templater}
   :cors-demo {:templater :stencil-templater}})

(defn new-co-dependency-map
  []
  {:docsite {:router :docsite-router
             :cors-demo-router :cors-demo-router}
   :user-manual {:router :docsite-router}
   :cors-demo {:router :cors-demo-router}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config :prod))
      (system-using (new-dependency-map))
      (system-co-using (new-co-dependency-map))))
