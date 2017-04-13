;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.1")

(defproject yada/aleph VERSION
  :pedantic? :abort
  :dependencies [[aleph "0.4.1" :exclusions [io.aleph/dirigiste]]
                 [yada/core ~VERSION]])
