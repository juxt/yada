;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0-SNAPSHOT")

(defproject yada/oauth2 VERSION
  :pedantic? :abort
  :exclusions [commons-codec]
  :dependencies [[yada/core ~VERSION]
                 [yada/jwt ~VERSION]
                 [yada/json ~VERSION]
                 [commons-codec "1.10"]])
