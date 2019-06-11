;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.3.0-alpha13")

(defproject yada/webjars VERSION
  :description "Support for webjars"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [org.webjars/webjars-locator "0.34"
                  ;; webjars-locator-core-0.35 brings in an
                  ;; out-of-date jackson-core
                  :exclusions [com.fasterxml.jackson.core/jackson-core]]])
