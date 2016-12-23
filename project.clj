;; Copyright © 2015, JUXT LTD.

(defproject yada "1.1.46"
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "http://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :exclusions [[org.clojure/clojure]]

  :dependencies
  [[bidi "2.0.14" :exclusions [ring/ring-core]]
   [buddy/buddy-sign "1.3.0"]
   [byte-streams "0.2.2"]
   [cheshire "5.6.3"]
   [clj-time "0.12.2"]

   [hiccup "1.0.5"]
   ;; We only need the back-end parts of json
   [json-html "0.4.0" :exclusions [hiccups]]
   [manifold "0.1.5"]
   [metosin/ring-http-response "0.8.0"]
   [metosin/ring-swagger "0.22.12" :exclusions [com.google.code.findbugs/jsr305]]

   [prismatic/schema "1.1.3"]
   [potemkin "0.4.3"]

   [org.clojure/core.async "0.2.395"]
   [org.clojure/data.codec "0.1.0"]
   [org.clojure/tools.reader "1.0.0-beta3"]

   ;; Built-in support for Cognitect transit
   [com.cognitect/transit-clj "0.8.297"]

   ;; Built-in Swagger UI
   [org.webjars/swagger-ui "2.2.6"]

   ;; Provide exclusions libraries
   [com.google.guava/guava "20.0"]

   ;; Webjars resources
   [org.webjars/webjars-locator "0.32"]
   ]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles
  {:dev {:main yada.dev.main
         :jvm-opts ["-Xms1g" "-Xmx1g"
                    "-server"
                    "-Dio.netty.leakDetectionLevel=paranoid"
                    ;; "-Dio.netty.allocator.numDirectArenas=0"
                    ]

         :pedantic? :abort

         :dependencies
         [[org.clojure/clojure "1.8.0"]
          [org.clojure/clojurescript "1.9.293"]

          ;; Exclusions
          [com.google.guava/guava "20.0"]

          [org.clojure/tools.logging "0.3.1"]

          [ch.qos.logback/logback-classic "1.1.8"
           :exclusions [org.slf4j/slf4j-api]]
          [org.slf4j/jul-to-slf4j "1.7.22"]
          [org.slf4j/jcl-over-slf4j "1.7.22"]
          [org.slf4j/log4j-over-slf4j "1.7.22"]

          [com.stuartsierra/component "0.3.2"]
          [org.clojure/tools.namespace "0.2.11"]
          [org.clojure/tools.nrepl "0.2.12"]

          [org.clojure/data.zip "0.1.2"]

          [markdown-clj "0.9.91"]
          [ring/ring-mock "0.3.0" :exclusions [ring/ring-codec]]

          [aero "1.0.1"]

          [juxt.modular/aleph "0.1.4" :exclusions [aleph]]
          [aleph "0.4.1"]
          [juxt.modular/bidi "0.9.5" :exclusions [bidi]]
          [juxt.modular/stencil "0.1.1" :exclusions [org.clojure/core.cache]]
          [juxt.modular/test "0.1.0"]
          [juxt.modular/template "0.6.3"]

          [org.webjars/jquery "2.1.3"]
          [org.webjars/bootstrap "3.3.6"]
          [org.webjars.bower/material-design-lite "1.2.1" :scope "test"]

          [cljsjs/react "15.4.0-0"]
          [reagent "0.6.0"]
          [re-frame "0.8.0"]
          [kibu/pushy "0.3.6"]

          ;; To compare with aleph http client
          [clj-http "3.4.1"]]

         :source-paths ^:replace ["dev/src"
                                  "examples/phonebook/src"
                                  "src"
                                  "ext/async/src"
                                  "ext/cheshire/src"
                                  "ext/json-html/src"
                                  "ext/jwt/src"
                                  "ext/multipart/src"
                                  "ext/oauth2/src"
                                  "ext/swagger/src"
                                  "ext/transit/src"
                                  "ext/yada/src"]

         :test-paths ["test"
                      "examples/phonebook/test"]

         :resource-paths ["dev/resources"
                          "examples/phonebook/resources"]}})
