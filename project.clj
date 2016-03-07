;; Copyright © 2015, JUXT LTD.

(defproject yada "1.1.0-SNAPSHOT"
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "http://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :exclusions [com.stuartsierra/component
               prismatic/schema
               org.clojure/clojure
               org.clojure/tools.reader]

  :dependencies
  [[bidi "2.0.1" :exclusions [ring/ring-core]]
   [buddy/buddy-sign "0.9.0"]
   [byte-streams "0.2.1" :exclusions [clj-tuple]]
   [camel-snake-kebab "0.3.2"]
   [cheshire "5.5.0"]
   [clj-time "0.11.0"]

   [hiccup "1.0.5"]
   [json-html "0.3.8" :exlusions [com.google.code.findbugs/jsr305]]
   [manifold "0.1.2"]
   [metosin/ring-http-response "0.6.5"]
   [metosin/ring-swagger "0.22.4" :exclusions [potemkin com.google.guava/guava com.google.code.findbugs/jsr305]]
   [com.google.code.findbugs/jsr305 "3.0.0"]

   [prismatic/schema "1.0.5"]
   [potemkin "0.4.3" :exclusions [riddley]]
   
   [org.clojure/core.async "0.2.374"]
   [org.clojure/data.codec "0.1.0"]
   [org.clojure/tools.reader "1.0.0-alpha1"]
   [org.clojure/tools.trace "0.7.9"]
   
   ;; TODO: Find out where this is being excluded, Schema needs it?
   [org.clojure/core.cache "0.6.4"]

   [com.cognitect/transit-clj "0.8.285"]

   ;; Built-in Swagger UI
   [org.webjars/swagger-ui "2.1.3"]
   ]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles
  {:dev {:main yada.dev.main
         :jvm-opts ["-Xms1g" "-Xmx1g" "-server" "-Dio.netty.leakDetectionLevel=advanced"]

         :plugins [[lein-cljsbuild "1.0.6"]
                   ;;[lein-less "1.7.5"]
                   [lein-figwheel "0.3.7" :exclusions [[org.clojure/clojure]
                                                       [org.codehaus.plexus/plexus-utils]]]]

         :cljsbuild {:builds
                     {:console {:source-paths ["console/src-cljs"]
                                :figwheel {:on-jsload "yada.console.core/reload-hook"}
                                :compiler {:output-to "target/cljs/console.js"
                                           :pretty-print true}}}}

         :exclusions [org.clojure/tools.nrepl]

         :pedantic? :abort

         :dependencies
         [[org.clojure/clojure "1.7.0"]
          [org.clojure/clojurescript "1.7.170"]

          [org.clojure/core.cache "0.6.4"]

          [org.clojure/tools.nrepl "0.2.12"] ; otherwise pedantic check fails
          [org.clojure/tools.logging "0.3.1"]

          [ch.qos.logback/logback-classic "1.1.5"
           :exclusions [org.slf4j/slf4j-api]]
          [org.slf4j/jul-to-slf4j "1.7.18"]
          [org.slf4j/jcl-over-slf4j "1.7.18"]
          [org.slf4j/log4j-over-slf4j "1.7.18"]

          [com.stuartsierra/component "0.3.1"]
          [org.clojure/tools.namespace "0.2.11"]
          [org.clojure/data.zip "0.1.1"]

          [markdown-clj "0.9.86"]
          [ring-mock "0.1.5"]

          [aero "0.1.5"]

          [juxt.modular/aleph "0.1.4" :excludes [aleph]]
          [aleph "0.4.1-beta5"]
          [juxt.modular/bidi "0.9.5" :exclusions [bidi]]
          [juxt.modular/stencil "0.1.1"]
          [juxt.modular/test "0.1.0"]
          [juxt.modular/template "0.6.3"]

          [org.webjars/jquery "2.1.3"]
          [org.webjars/bootstrap "3.3.6"]
          [org.webjars.bower/material-design-lite "1.0.2" :scope "test"]

          [cljsjs/react "0.13.3-1"]
          [reagent "0.5.0"]
          [re-frame "0.4.1"]
          [kibu/pushy "0.3.2"]]

         :source-paths ["dev/src"
                        "examples/phonebook/src"]
         
         :test-paths ["test"
                      "examples/phonebook/test"]

         :resource-paths ["dev/resources"
                          "examples/phonebook/resources"]}
   #_:test
   #_{:source-paths ["yada.test/src"]}

   })
