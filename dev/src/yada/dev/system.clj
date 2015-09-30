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
   [modular.component.co-dependency :as co-dependency]

   [modular.bidi :refer (new-router new-web-resources new-archived-web-resources new-redirect)]
   [yada.dev.docsite :refer (new-docsite)]
   [yada.dev.console :refer (new-console)]
   [yada.dev.cors-demo :refer (new-cors-demo)]
   [yada.dev.talks :refer (new-talks)]
   [yada.dev.user-manual :refer (new-user-manual)]
   [yada.dev.database :refer (new-database)]
   [yada.dev.user-api :refer (new-verbose-user-api)]
   [modular.aleph :refer (new-webserver)]
   [modular.component.co-dependency :refer (co-using system-co-using)]

   [yada.dev.async :refer (new-handler)]
   [yada.dev.config :as config]
   [yada.dev.hello :refer (new-hello-world-example)]
   [yada.dev.error-example :refer (new-error-example)]

   [phonebook.system :as phonebook]))

(defn database-components [system]
  (assoc system :database (new-database)))

(defn api-components [system]
  (assoc system :user-api (new-verbose-user-api)))

(defn docsite-components [system config]
  (assoc
   system
   :user-manual (new-user-manual :prefix (config/docsite-origin config))

   :docsite (new-docsite :config config)
   :jquery (new-web-resources
            :key :jquery
            :uri-context "/jquery"
            :resource-prefix "META-INF/resources/webjars/jquery/2.1.3")
   :bootstrap (new-web-resources
               :key :bootstrap
               :uri-context "/bootstrap"
               :resource-prefix "META-INF/resources/webjars/bootstrap/3.3.2")
   :web-resources (new-web-resources
                   :uri-context "/static"
                   :resource-prefix "static")
   :highlight-js-resources
   (new-archived-web-resources :archive (io/resource "highlight.zip") :uri-context "/hljs/")
   :console (new-console config)
   ))

(defn swagger-ui-components [system]
  (assoc system
         :swagger-ui
         (new-web-resources
          :key :swagger-ui
          :uri-context "/swagger-ui"
          :resource-prefix "META-INF/resources/webjars/swagger-ui/2.1.1")))

(defn http-server-components [system config]
  (assoc system
         :docsite-server
         (new-webserver
          :port (config/docsite-port config)
          ;; raw-stream? = true gives us a manifold stream of io.netty.buffer.ByteBuf instances
          ;; Use to convert to a stream bs/to-input-stream
          :raw-stream? true)

         :docsite-router (new-router)

         :console-server (new-webserver :port (config/console-port config))
         :console-router (new-router)

         :cors-demo-server (new-webserver :port (config/cors-demo-port config))
         :cors-demo-router (new-router)

         :talks-server (new-webserver :port (config/talks-port config))
         :talks-router (new-router)
         ))

(defn hello-world-components [system config]
  (assoc system :hello-world (new-hello-world-example config)))

(defn error-components [system]
  (assoc system :error-example (new-error-example)))

(defn cors-demo-components [system config]
  (assoc system :cors-demo (new-cors-demo config)))

(defn talks-components [system config]
  (assoc system :talks (new-talks config)))

(defn phonebook-system [system]
  (assoc system :phonebook
         (-> (phonebook/new-system-map)
             (system-using (phonebook/new-dependency-map))
             (co-dependency/system-co-using (phonebook/new-co-dependency-map)))))

(defn new-system-map
  [config]
  (apply system-map
    (apply concat
      (-> {}
        (database-components)
        (api-components)
        (docsite-components config)
        (swagger-ui-components)
        (http-server-components config)
        (hello-world-components config)
        (error-components)
        (cors-demo-components config)
        (talks-components config)
        (phonebook-system)

        (assoc :docsite-redirect (new-redirect :from "/" :to :yada.dev.docsite/index))
        (assoc :console-redirect (new-redirect :from "/" :to :yada.dev.console/index))
        (assoc :cors-demo-redirect (new-redirect :from "/" :to :yada.dev.cors-demo/index))
        (assoc :talks-redirect (new-redirect :from "/" :to :yada.dev.talks/index))))))

(defn new-dependency-map
  []
  {:docsite-server {:request-handler :docsite-router}
   :console-server {:request-handler :console-router}
   :cors-demo-server {:request-handler :cors-demo-router}
   :talks-server {:request-handler :talks-router}

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

   :console-router [:console
                    :console-redirect]

   :cors-demo-router [:cors-demo
                      :jquery :bootstrap
                      :web-resources
                      :highlight-js-resources
                      :cors-demo-redirect]

   :talks-router [:talks
                  :talks-redirect]})

(defn new-co-dependency-map
  []
  {:docsite {:router :docsite-router
             :cors-demo-router :cors-demo-router
             :talks-router :talks-router}
   :user-manual {:router :docsite-router}
   :console {:router :console-router}
   :cors-demo {:router :cors-demo-router}
   :talks {:router :talks-router}})

(defn new-production-system
  "Create the production system"
  []
  (-> (new-system-map (config/config :prod))
      (system-using (new-dependency-map))
      (system-co-using (new-co-dependency-map))))
