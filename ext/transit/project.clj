;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0-SNAPSHOT")

(defproject yada/transit VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [com.cognitect/transit-clj "0.8.297"]])
