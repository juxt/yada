;; Copyright © 2014-2016, JUXT LTD.

(defproject lean "1.0.0"
  :description "A minimal project using bidi and yada."
  :url "http://github.com/yada"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [yada "1.1.46"
    :exclusions
    [buddy/buddy-sign
     cheshire
     com.cognitect/transit-clj
     ;; hiccup - we need hiccup because of exception formatting
     json-html
;;     manifold
     metosin/ring-http-response
     metosin/ring-swagger
     org.clojure/core.async
     org.webjars/swagger-ui
     org.webjars/webjars-locator
     prismatic/schema

     ;; ???  Could not transfer artifact
     ;; com.fasterxml.jackson.core:jackson-databind:jar:2.2.3
     ;; from/to central
     ;; (https://repo1.maven.org/maven2/):
     ;; repo1.maven.org
     ;;
     ;; But it's already in my Maven repo!
     com.fasterxml.jackson.core/jackson-databind
     ]]
   [aleph "0.4.1"]
   [bidi "2.0.12"]]

  ;; com.fasterxml.jackson.core:jackson-databind:jar:2.2.3

  :pedantic? :abort
  )
