;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0-SNAPSHOT")

(defproject yada/bidi VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [bidi "2.0.16"]])
