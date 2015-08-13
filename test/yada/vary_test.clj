;; Copyright © 2015, JUXT LTD.

(ns yada.vary-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.yada :as yada]
   [ring.mock.request :refer (request)]
   [juxt.iota :refer (given)]
   [schema.test :as st]
   [yada.representation :refer (coerce-representations)]))

#_(st/deftest vary-test
  (given
    (vary :get
          (coerce-representations [{:content-type #{"text/plain" "text/html"}}]))
    identity := #{:content-type})

  (given
    (vary :get
          (coerce-representations [{:charset #{"UTF-8" "Latin-1"}}]))
    identity := #{:charset})

  (given
    (vary :get
          (coerce-representations [{:content-type #{"text/plain" "text/html"}
                                   :charset #{"UTF-8" "Latin-1"}}]))
    identity := #{:content-type :charset})

  (testing "method guards"
      (given
        (vary :post
              (coerce-representations [{:method #{:post}
                                       :content-type #{"application/json"}}
                                      {:method #{:get}
                                       :content-type #{"text/plain" "text/html"}
                                       :charset #{"UTF-8" "Latin-1"}}]))
        identity := nil)
    (given
        (vary :get
              (coerce-representations [{:content-type #{"application/json"}}
                                      {:method #{:get}
                                       :content-type #{"text/plain"}
                                       :charset #{"UTF-8"}}]))
        identity := #{:content-type})))

#_(st/deftest vary-header-test []
  (let [resource "Hello World!"
        handler (yada/resource resource {:content-type #{"text/plain" "text/html"}})
        request (request :head "/")
        response @(handler request)]
    (given response
      :status := 200
      [:headers "vary"] :? some?
      [:headers "vary" parse-csv] := #{"accept"}
      )))
