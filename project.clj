(defproject yada "0.1.0-SNAPSHOT"
  :description "A library for Clojure web APIs"
  :url "http://github.com/juxt/yada"

  :exclusions [com.stuartsierra/component org.clojure/clojure]

  :dependencies
  [
   [org.clojure/clojure "1.7.0-alpha4"]
   [bidi "2.0.0-SNAPSHOT"]
   [prismatic/schema "0.3.3" :exclusions [potemkin]]
   [manifold "0.1.0-beta5"]
   ]

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [
                                  [com.stuartsierra/component "0.2.2"]
                                  [org.clojure/tools.namespace "0.2.5"]
                                  [juxt.modular/maker "0.5.0"]
                                  [juxt.modular/aleph "0.0.1-SNAPSHOT"]
                                  [ring-mock "0.1.5"]]

                   :source-paths ["dev" "examples"]}})
