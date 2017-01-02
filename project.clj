;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0")

(defproject yada/core VERSION
  :description "A powerful Clojure web library, full HTTP, full async"
  :url "https://github.com/juxt/yada"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :exclusions [[org.clojure/clojure]]

  :pedantic? :abort

  :dependencies
  [[byte-streams "0.2.2"]
   [clj-time "0.12.2"]
   [hiccup "1.0.5"]
   [manifold "0.1.4"]
   [org.clojure/data.codec "0.1.0"]
   [org.clojure/tools.reader "1.0.0-beta3"]
   [potemkin "0.4.3"]
   [prismatic/schema "1.1.3"]
   [ring/ring-core "1.6.0-beta6"]]

  :profiles
  {:test
   {
    :dependencies
    [[org.clojure/clojure "1.8.0"]

     ;; Logging
     [org.clojure/tools.logging "0.3.1"]
     [ch.qos.logback/logback-classic "1.1.8"
      :exclusions [org.slf4j/slf4j-api]]
     [org.slf4j/jul-to-slf4j "1.7.22"]
     [org.slf4j/jcl-over-slf4j "1.7.22"]
     [org.slf4j/log4j-over-slf4j "1.7.22"]

     ;; Testing
     [ring/ring-mock "0.3.0"]

     ;; webjars testing needs this in the path
     [org.webjars/bootstrap "3.3.6"]

     ;; ext dependencies
     [bidi "2.0.16"]
     [aleph "0.4.1" :exclusions [io.aleph/dirigiste]]
     [org.clojure/core.async "0.2.395"]
     [cheshire "5.6.3"]
     [json-html "0.4.0" :exclusions [hiccups]]
     [buddy/buddy-sign "1.3.0"]
     [commons-codec "1.10"]
     [metosin/ring-swagger "0.22.12" :exclusions [org.clojure/clojure]]
     [org.webjars/swagger-ui "2.2.6"]
     [com.cognitect/transit-clj "0.8.297"]
     [org.webjars/webjars-locator "0.32"]]

    :source-paths
    [
     ;; We use the full bundle, that contains the most
     ;; test coverage. Tests for other bundles can be
     ;; run from the bundle directory itself.
     "bundles/full/src"

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

    :test-paths ["bundles/full/test"]}

   :dev
   [:test ; so we can run the tests from our REPL
    {:jvm-opts
     ["-Xms1g" "-Xmx1g"
      "-server"
      "-Dio.netty.leakDetectionLevel=paranoid"
      "-Dyada.dir=."]

     :repl-options {:init-ns user
                    :welcome (println "Type (dev) to start")}

     :plugins [[cider/cider-nrepl "0.14.0"]]

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
