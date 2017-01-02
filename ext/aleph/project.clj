;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0")

(defproject yada/aleph VERSION
  :pedantic? :abort
  :dependencies [[aleph "0.4.1" :exclusions [io.aleph/dirigiste]]
                 [yada/core ~VERSION]])
