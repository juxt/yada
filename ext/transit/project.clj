;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.3.0-alpha9")

(defproject yada/transit VERSION
  :description "Support for transit media-types"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION]
                 [com.cognitect/transit-clj "0.8.313"]
                 [com.cognitect/transit-java "0.8.337" :exclusions [commons-codec]]])
