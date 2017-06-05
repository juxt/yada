;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.5")

(defproject yada/aleph-next VERSION
  :description "Experimental use of next version of Aleph"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[aleph "0.4.2-alpha8"]
                 [manifold "0.1.6-alpha1"]
                 [yada/core ~VERSION]])
