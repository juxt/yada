;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0")

(defproject yada/jwt VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [buddy/buddy-sign "1.3.0"]])
