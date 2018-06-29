;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.13")

(defproject yada/core VERSION
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "https://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :exclusions [[org.clojure/clojure]]

  :pedantic? :abort

  :dependencies
  [[byte-streams "0.2.4-alpha4"]
   [clj-time "0.14.2"]
   [hiccup "1.0.5"]
   [manifold "0.1.7-alpha5"]
   [org.clojure/data.codec "0.1.0"]
   [org.clojure/tools.reader "1.0.0-beta4"]
   [potemkin "0.4.4"]
   [prismatic/schema "1.1.7"]
   [ring/ring-core "1.6.3"]]

  :global-vars {*warn-on-reflection* true}

  :profiles
  {:test
   {
    :dependencies
    [[org.clojure/clojure "1.9.0"]

     ;; Logging
     [org.clojure/tools.logging "0.3.1"]
     [ch.qos.logback/logback-classic "1.1.8"
      :exclusions [org.slf4j/slf4j-api]]
     [org.slf4j/jul-to-slf4j "1.7.22"]
     [org.slf4j/jcl-over-slf4j "1.7.22"]
     [org.slf4j/log4j-over-slf4j "1.7.22"]

     ;; Testing
     [ring/ring-mock "0.3.0"]
     [juxt/iota "0.2.3"]

     ;; webjars testing needs this in the path
     [org.webjars/bootstrap "3.3.6"]

     ;; ext dependencies
     [bidi "2.1.3"]
     [aleph "0.4.4" :exclusions [io.aleph/dirigiste]]
     [org.clojure/core.async "0.3.442"]
     [cheshire "5.8.0" :exclusions [com.fasterxml.jackson.core/jackson-core]]
     [json-html "0.4.0" :exclusions [hiccups]]
     [buddy/buddy-sign "2.2.0"]
     [commons-codec "1.10"]
     [metosin/ring-swagger "0.26.0" :exclusions [org.clojure/clojure]]
     [org.webjars/swagger-ui "2.2.6"]
     [com.cognitect/transit-clj "0.8.297" :exclusions [com.fasterxml.jackson.core/jackson-core]]
     [org.webjars/webjars-locator "0.34" :exclusions [com.fasterxml.jackson.core/jackson-core]]
     ;; Required because of above exclusions
     [com.fasterxml.jackson.core/jackson-core "2.9.0"]]


    :source-paths
    [
     ;; We use the 'default' bundle, that contains the most
     ;; test coverage. Tests for other bundles can be
     ;; run from the bundle directory itself.
     "bundles/default/src"

     ;; source directories of dependency modules.
     "ext/aleph/src"
     "ext/async/src"
     "ext/bidi/src"
     "ext/json/src"
     "ext/json-html/src"
     "ext/jwt/src"
     "ext/multipart/src"
     "ext/oauth2/src"
     "ext/swagger/src"
     "ext/transit/src"
     "ext/webjars/src"]

    :test-paths ["bundles/default/test"]}

   :dev
   [:test                      ; so we can run the tests from our REPL
    {:jvm-opts
     ["-Xms1g" "-Xmx1g"
      "-server"
      "-Dio.netty.leakDetectionLevel=paranoid"
      "-Dyada.dir=."]

     :repl-options {:init-ns user
                    :welcome (println "Type (dev) to start")}

     :global-vars {*warn-on-reflection* true}

     :dependencies
     [
      ;; REPL and dev workflow
      [org.clojure/tools.namespace "0.2.11"]
      [org.clojure/tools.nrepl "0.2.12"]
      [com.stuartsierra/component "0.3.2"]
      [aero "1.0.1"]

      ;; Publishing user manual
      [camel-snake-kebab "0.4.0"]
      [org.asciidoctor/asciidoctorj "1.6.0-alpha.3"]
      [markdown-clj "0.9.91"]]

     :source-paths ["dev/src"]
     :resource-paths ["dev/resources"]}]})
