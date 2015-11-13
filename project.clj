;; Copyright © 2015, JUXT LTD.

(defproject yada "1.0.0-SNAPSHOT"
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "http://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :exclusions [com.stuartsierra/component
               prismatic/schema
               org.clojure/clojure
               org.clojure/tools.reader]

  :dependencies
  [[bidi "1.21.0" :exclusions [ring/ring-core]]
   [byte-streams "0.2.0" :exclusions [clj-tuple]]
   [camel-snake-kebab "0.1.4"]
   [cheshire "5.4.0"]
   [clj-time "0.9.0"]
   [hiccup "1.0.5"]
   [json-html "0.3.1"]
   [manifold "0.1.1-alpha4"]
   [metosin/ring-http-response "0.6.5"]
   [metosin/ring-swagger "0.20.2" :exclusions [potemkin]]
   [prismatic/schema "0.4.3" :exclusions [potemkin]]
   [potemkin "0.4.1" :exclusions [riddley]]
   [ring-basic-authentication "1.0.5"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [org.clojure/tools.reader "0.9.1"]
   [org.clojars.ikoblik/clj-index "0.0.2"]
   [com.cognitect/transit-clj "0.8.285"]]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles
  {:dev {:main yada.dev.main

         :plugins [[lein-cljsbuild "1.0.6"]
                   ;;[lein-less "1.7.5"]
                   [lein-figwheel "0.3.7" :exclusions [[org.clojure/clojure]
                                                       [org.codehaus.plexus/plexus-utils]
                                                       #_[org.clojure/tools.reader]
                                                       ]]]

         :cljsbuild {:builds
                     {:console {:source-paths ["console/src-cljs"]
                                :figwheel {:on-jsload "yada.console.core/reload-hook"}
                                :compiler {:output-to "target/cljs/console.js"
                                           :pretty-print true}}}}

         :dependencies
         [[org.clojure/clojure "1.7.0"]
          [org.clojure/clojurescript "1.7.58"]


          [org.clojure/tools.logging "0.3.1"]
          [org.clojure/tools.trace "0.7.8"]

          [ch.qos.logback/logback-classic "1.0.7"
           :exclusions [org.slf4j/slf4j-api]]
          [org.slf4j/jul-to-slf4j "1.7.2"]
          [org.slf4j/jcl-over-slf4j "1.7.2"]
          [org.slf4j/log4j-over-slf4j "1.7.2"]

          [com.stuartsierra/component "0.2.3"]
          [org.clojure/tools.namespace "0.2.5"]
          [org.clojure/data.zip "0.1.1"]

          [markdown-clj "0.9.62"]
          [ring-mock "0.1.5"]

          [aero "0.1.2"]
          [juxt/iota "0.2.2"]

          [juxt.modular/aleph "0.1.3" :exclusions [manifold]]
          [juxt.modular/bidi "0.9.2" :exclusions [bidi]]
          [juxt.modular/stencil "0.1.0"]
          [juxt.modular/co-dependency "0.2.2"]
          [juxt.modular/test "0.1.0"]
          [juxt.modular/template "0.6.3"]

          [org.webjars/swagger-ui "2.1.1"]
          [org.webjars/jquery "2.1.3"]
          [org.webjars/bootstrap "3.3.2"]
          [org.webjars.bower/material-design-lite "1.0.2" :scope "test"]

          [cljsjs/react "0.13.3-1"]
          [reagent "0.5.0"]
          [re-frame "0.4.1"]
          [kibu/pushy "0.3.2"]]

         :source-paths ["dev/src" "examples/phonebook/src"]
         :test-paths ["test" "examples/phonebook/test"]
         :resource-paths ["dev/resources" "examples/phonebook/resources"]}
   :test
   {:source-paths ["yada.test/src"]}

   })
