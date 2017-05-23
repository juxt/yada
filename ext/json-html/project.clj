;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.4")

(defproject yada/json-html VERSION
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [json-html "0.4.0" :exclusions [hiccups]]
                 ])
