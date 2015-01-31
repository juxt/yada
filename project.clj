;; Copyright © 2015, JUXT LTD.

(defproject yada "0.1.0-SNAPSHOT"
  :description "A library for Clojure web APIs"
  :url "http://github.com/juxt/yada"

  :exclusions [com.stuartsierra/component
               org.clojure/clojure]

  :dependencies
  [[org.clojure/clojure "1.7.0-alpha4"]
   [prismatic/schema "0.3.5" :exclusions [potemkin]]
   [manifold "0.1.0-beta8"]
   [potemkin "0.3.11"]
   [hiccup "1.0.5"]
   [cheshire "5.4.0"]
   [camel-snake-kebab "0.1.4"]]

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies
                   [
                    [com.stuartsierra/component "0.2.2"]
                    [bidi "1.16.0"]
                    [org.clojure/tools.namespace "0.2.5"]
                    [juxt.modular/maker "0.5.0"]
                    [juxt.modular/bidi "0.7.3" :exclusions [bidi]]
                    [juxt.modular/aleph "0.0.3"]
                    [juxt.modular/test "0.1.0"]
                    [ring-mock "0.1.5"]
                    [org.webjars/swagger-ui "2.1.0-alpha.6"]
                    [malcolmsparks/co-dependency "0.1.5"]

                    ;; while bidi is in checkouts
                    [compojure "1.1.6"]
                    ]

                   :source-paths ["dev" "examples"]}})
