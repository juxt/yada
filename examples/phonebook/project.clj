;; Copyright © 2015, JUXT LTD.

(defproject yada/phonebook "1.0.0-SNAPSHOT"
  :description "A simple example of defining yada resource types"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[aleph "0.4.0" :exclusions [org.clojure/clojure]]
                 [com.stuartsierra/component "0.2.3"]
                 [hiccup "1.0.5" :exclusions [org.clojure/clojure]]
                 [juxt.modular/co-dependency "0.2.1"]
                 [org.clojure/tools.namespace "0.2.5"]
                 [yada "1.0.0-SNAPSHOT" :exclusions [clj-tuple riddley potemkin manifold]]]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.7.0"]
                                  [ring-mock "0.1.5"]]
                   :source-paths ["dev"]}})
