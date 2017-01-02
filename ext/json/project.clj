;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0")

(defproject yada/json VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [cheshire "5.6.3"]])
