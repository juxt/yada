;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.3.0-alpha7-SNAPSHOT")

(defproject yada/aleph VERSION
  :description "yada integration with Aleph (and Netty)"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[aleph "0.4.4" :exclusions [io.aleph/dirigiste byte-streams manifold riddley potemkin]]
                 [yada/core ~VERSION]])
