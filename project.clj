;; Copyright © 2015, JUXT LTD.

(defproject yada "1.1.30"
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "http://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies
  [[bidi "2.0.8" :exclusions [ring/ring-core]]
   [buddy/buddy-sign "0.9.0"]
   [byte-streams "0.2.2"]
   [camel-snake-kebab "0.4.0"]
   [cheshire "5.6.1"]
   [clj-time "0.11.0"]

   [hiccup "1.0.5"]
   ;; We only need the back-end parts of json
   [json-html "0.4.0" :exclusions [hiccups]]
   [manifold "0.1.4"]
   [metosin/ring-http-response "0.6.5"]
   [metosin/ring-swagger "0.22.7"]

   [prismatic/schema "1.1.1"]
   [potemkin "0.4.3"]

   [org.clojure/core.async "0.2.374"]
   [org.clojure/data.codec "0.1.0"]
   [org.clojure/tools.reader "1.0.0-beta1"]

   ;; Built-in support for Cognitect transit
   [com.cognitect/transit-clj "0.8.285"]

   ;; Built-in Swagger UI
   [org.webjars/swagger-ui "2.1.4"]

   ;; Provide exclusions libraries
   [com.google.guava/guava "18.0"]

   ;; Webjars resources
   [org.webjars/webjars-locator "0.27"]
   ]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles
  {:dev {:main yada.dev.main
         :jvm-opts ["-Xms1g" "-Xmx1g"
                    "-server"
                    "-Dio.netty.leakDetectionLevel=advanced"
                    "-Dio.netty.allocator.numDirectArenas=0"]

         :pedantic? :abort

         :dependencies
         [[org.clojure/clojure "1.8.0"]
          [org.clojure/clojurescript "1.7.170"]

          ;; Exclusions
          [com.google.guava/guava "18.0"]

          [org.clojure/tools.logging "0.3.1"]

          [ch.qos.logback/logback-classic "1.1.5"
           :exclusions [org.slf4j/slf4j-api]]
          [org.slf4j/jul-to-slf4j "1.7.18"]
          [org.slf4j/jcl-over-slf4j "1.7.18"]
          [org.slf4j/log4j-over-slf4j "1.7.18"]

          [com.stuartsierra/component "0.3.1"]
          [org.clojure/tools.namespace "0.2.11"]
          [org.clojure/tools.nrepl "0.2.12"]

          [org.clojure/data.zip "0.1.1"]

          [markdown-clj "0.9.86"]
          [ring-mock "0.1.5"]

          [aero "1.0.0-beta3"]

          [juxt.modular/aleph "0.1.4" :excludes [aleph]]
          [aleph "0.4.1"]
          [juxt.modular/bidi "0.9.5" :exclusions [bidi]]
          [juxt.modular/stencil "0.1.1" :exclusions [org.clojure/core.cache]]
          [juxt.modular/test "0.1.0"]
          [juxt.modular/template "0.6.3"]

          [org.webjars/jquery "2.1.3"]
          [org.webjars/bootstrap "3.3.6"]
          [org.webjars.bower/material-design-lite "1.0.2" :scope "test"]

          [cljsjs/react "0.13.3-1"]
          [reagent "0.5.0"]
          [re-frame "0.4.1"]
          [kibu/pushy "0.3.2"]

          ;; To compare with aleph http client
          [clj-http "2.1.0"]]

         :source-paths ["dev/src"
                        "examples/phonebook/src"]

         :test-paths ["test"
                      "examples/phonebook/test"]

         :resource-paths ["dev/resources"
                          "examples/phonebook/resources"]}})
