;; Copyright © 2015, JUXT LTD.

(ns yada.vary-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.yada :as yada]
   [yada.charset :as charset]
   [yada.util :refer (parse-csv)]
   [ring.mock.request :refer (request)]
   [juxt.iota :refer (given)]
   [schema.test :as st]
   [yada.representation :refer (coerce-representations representation-seq vary)]))

;; TODO: Fix up these tests

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
        handler (yada/resource resource {:media-type #{"text/plain" "text/html"}})
        request (request :head "/")
        response @(handler request)]
    (given response
      :status := 200
      [:headers "vary"] :? some?
      [:headers "vary" parse-csv set] := #{"accept"}
      )))
