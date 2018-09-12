;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.14-SNAPSHOT")

(defproject yada/lean VERSION
  :description "A stripped-down batteries-not-included bundle of yada"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/aleph ~VERSION]
                 [yada/bidi ~VERSION]
                 [yada/core ~VERSION]]

  :profiles
  {:dev
   {:jvm-opts ["-Xms1g" "-Xmx1g"
               "-server"
               "-Dio.netty.leakDetectionLevel=paranoid"
               "-Dyada.dir=../../"]

    :dependencies
    [[org.clojure/clojure "1.8.0"]

     ;; Testing
     [ring/ring-mock "0.3.0" :exclusions [ring/ring-codec]]

     ;; REPL and dev workflow
     [org.clojure/tools.namespace "0.2.11"]
     [org.clojure/tools.nrepl "0.2.13"]
     [com.stuartsierra/component "0.3.2"]
     [aero "1.0.1"]

     ;; Logging
     [org.clojure/tools.logging "0.3.1"]
     [ch.qos.logback/logback-classic "1.1.8"
      :exclusions [org.slf4j/slf4j-api]]
     [org.slf4j/jul-to-slf4j "1.7.22"]
     [org.slf4j/jcl-over-slf4j "1.7.22"]
     [org.slf4j/log4j-over-slf4j "1.7.22"]

     ;; Publishing user manual
     [camel-snake-kebab "0.4.0"]
     [org.asciidoctor/asciidoctorj "1.6.0-alpha.3"]
     [markdown-clj "0.9.91"]
     [juxt/iota "0.2.3" :scope "test"]

     ]

    :source-paths ["../../dev/src" ;; yada.yada
                   "../../src" ;; core source
                   ;; Extension sources
                   "../../ext/aleph/src"
                   "../../ext/bidi/src"]

    ;; For logback.xml
    :resource-paths ["../../dev/resources"]

    :test-paths ["../../test"]}})
