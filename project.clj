(defproject ecto "0.1.0-SNAPSHOT"
  :description "A library for Clojure web APIs"
  :url "http://github.com/juxt/ecto"

  :exclusions [com.stuartsierra/component]

  :dependencies
  [
   [com.stuartsierra/component "0.2.2"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [bidi "1.12.2"]
   [juxt.modular/aleph "0.0.1"]
   [juxt.modular/maker "0.5.0"]
   [juxt.modular/wire-up "0.5.0"]
   [org.clojure/clojure "1.7.0-alpha4"]
   [org.clojure/tools.reader "0.8.9"]
   [prismatic/plumbing "0.2.2"]
   [prismatic/schema "0.2.1" :exclusions [potemkin]]
   [ring-mock "0.1.5"]
   [org.clojure/tools.reader "0.8.3"]
   [cheshire "5.3.1"]
   ]

  :main ecto.main

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.5"]]
                   :source-paths ["dev"
                                  ]}})
