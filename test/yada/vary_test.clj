;; Copyright © 2015, JUXT LTD.

(ns yada.vary-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [juxt.iota :refer (given)]
   [ring.mock.request :refer (request)]
   [schema.test :as st]
   [yada.charset :as charset]
   [yada.representation :refer (coerce-representations representation-seq vary)]
   [yada.util :refer (parse-csv)]
   [yada.yada :as yada :refer [yada]]))

(st/deftest vary-test
  (given
    (vary
     (representation-seq (coerce-representations [{:media-type #{"text/plain" "text/html"}}])))
    identity := #{:media-type})

  (given
    (vary
     (representation-seq (coerce-representations [{:charset #{"UTF-8" "Latin-1"}}])))
    identity := #{:charset})

  (given
    (vary
     (representation-seq (coerce-representations [{:media-type #{"text/plain" "text/html"}
                                                   :charset #{"UTF-8" "Latin-1"}}])))
    identity := #{:media-type :charset})

  )

(st/deftest vary-header-test []
  (let [resource "Hello World!"
        handler (yada resource #_{:produces [{:media-type #{"text/plain" "text/html"}}]})
        request (request :head "/")
        response @(handler request)]
    (given response
      :status := 200
      [:headers "vary"] :? some?
      [:headers "vary" parse-csv set] := #{"accept"}
      )))
