(defproject ecto "0.1.0-SNAPSHOT"
  :description "A library for Clojure web APIs"
  :url "http://github.com/juxt/ecto"

  :exclusions [com.stuartsierra/component org.clojure/clojure]

  :dependencies
  [
;;   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
;;   [org.clojure/core.match "0.3.0-alpha4"]
   [bidi "2.0.0-SNAPSHOT"]
   [metosin/ring-swagger "0.15.0"]
   [org.clojure/clojure "1.7.0-alpha4"]
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
