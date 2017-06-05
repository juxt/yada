;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.5")

(defproject yada/webjars VERSION
  :description "Support for webjars"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [org.webjars/webjars-locator "0.32"]])
