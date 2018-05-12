;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.12")

(defproject yada/swagger VERSION
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :pedantic? :abort
  :dependencies [[yada/core ~VERSION :exclusions [commons-codec]]
                 [yada/jwt ~VERSION]
                 [yada/bidi ~VERSION]
                 [yada/webjars ~VERSION]
                 [metosin/ring-swagger "0.22.12" :exclusions [org.clojure/clojure]]
                 [org.webjars/swagger-ui "2.2.6"]]
  )
