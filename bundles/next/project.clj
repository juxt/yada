;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.15")

(defproject yada/next VERSION
  :description "Experimental 'next' bundle of yada"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}

  :pedantic? :abort

  :dependencies [[yada/aleph ~VERSION]
                 [yada/bidi ~VERSION]
                 [yada/core ~VERSION]
                 [yada/json ~VERSION]
                 [yada/json-html ~VERSION]
                 [yada/jwt ~VERSION]
                 [yada/multipart ~VERSION]
                 [yada/oauth2 ~VERSION]
                 [yada/transit ~VERSION]
                 [yada/webjars ~VERSION]]

  :profiles {:test {:dependencies [[org.clojure/clojure "1.9.0-alpha14"]]}})
