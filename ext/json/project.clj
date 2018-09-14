;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.14")

(defproject yada/json VERSION
  :description "Support for application/json media types"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [cheshire "5.8.0"]])
