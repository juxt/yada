;; Copyright © 2014-2016, JUXT LTD.

(def VERSION "1.2.0-SNAPSHOT")

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
                 [yada/webjars ~VERSION]])
