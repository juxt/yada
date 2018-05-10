;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.11")

(defproject yada/jwt VERSION
  :description "JSON Web Token support"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [buddy/buddy-sign "2.2.0"]])
