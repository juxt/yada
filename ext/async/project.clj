;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.4")

(defproject yada/async VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [org.clojure/core.async "0.2.395"]])
