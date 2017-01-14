;; Copyright © 2014-2017, JUXT LTD.

(def VERSION "1.2.0")

(defproject yada VERSION

  :pedantic? :abort

  :dependencies [[yada/aleph ~VERSION]
                 [yada/async ~VERSION]
                 [yada/bidi ~VERSION]
                 [yada/core ~VERSION]
                 [yada/json ~VERSION]
                 [yada/json-html ~VERSION]
                 [yada/jwt ~VERSION]
                 [yada/multipart ~VERSION]
                 [yada/oauth2 ~VERSION]
                 [yada/swagger ~VERSION]
                 [yada/transit ~VERSION]
                 [yada/webjars ~VERSION]]

  :profiles {:test {:dependencies [[org.clojure/clojure "1.8.0"]
                                   [org.webjars/bootstrap "3.3.6"]]}})
