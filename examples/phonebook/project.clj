;; Copyright © 2015, JUXT LTD.

(defproject yada/phonebook "1.1.3"
  :description "A simple example of defining yada resource types"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[aero "0.2.1" :exclusions [prismatic/schema]]
                 [aleph "0.4.1-beta5" :exclusions [org.clojure/clojure byte-streams]]
                 [com.stuartsierra/component "0.3.1"]
                 [hiccup "1.0.5" :exclusions [org.clojure/clojure]]
                 [org.clojure/tools.namespace "0.2.11"]
                 [yada "1.1.3" :exclusions [clj-tuple riddley potemkin manifold]]]

  :pedantic? :abort

  :repl-options {:init-ns user
                 :welcome (println "Type (dev) to start")}

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [ring-mock "0.1.5" :exclusions [commons-codec]]]
                   :source-paths ["dev"]}})
