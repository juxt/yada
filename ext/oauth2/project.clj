;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.17-SNAPSHOT")

(defproject yada/oauth2 VERSION
  :description "OAuth2 support"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :exclusions [commons-codec]
  :dependencies [[yada/core ~VERSION]
                 [yada/jwt ~VERSION]
                 [yada/json ~VERSION]
                 [commons-codec "1.10"]])
