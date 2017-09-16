;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.9")

(defproject yada/async VERSION
  :description "core.async integration"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [org.clojure/core.async "0.3.443"]])
