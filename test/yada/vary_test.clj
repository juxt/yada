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
     (representation-seq (coerce-representations [{:content-type #{"text/plain" "text/html"}}])))
    identity := #{:content-type})

  (given
    (vary
     (representation-seq (coerce-representations [{:charset #{"UTF-8" "Latin-1"}}])))
    identity := #{:charset})

  (given
    (vary
     (representation-seq (coerce-representations [{:content-type #{"text/plain" "text/html"}
                                                   :charset #{"UTF-8" "Latin-1"}}])))
    identity := #{:content-type :charset})

  )

(st/deftest vary-header-test []
  (let [resource "Hello World!"
        handler (yada/resource resource {:content-type #{"text/plain" "text/html"}})
        request (request :head "/")
        response @(handler request)]
    (given response
      :status := 200
      [:headers "vary"] :? some?
      [:headers "vary" parse-csv set] := #{"accept"}
      )))
