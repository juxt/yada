;; Copyright © 2015, JUXT LTD.

(ns yada.vary-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.yada :as yada]
   [ring.mock.request :refer (request)]
   [juxt.iota :refer (given)]
   [schema.test :as st]
   [yada.negotiation :refer (vary parse-representations)]))

(st/deftest vary-test
  (given
    (vary :get
          (parse-representations [{:content-type #{"text/plain" "text/html"}}]))
    identity := #{:content-type})

  (given
    (vary :get
          (parse-representations [{:charset #{"UTF-8" "Latin-1"}}]))
    identity := #{:charset})

  (given
    (vary :get
          (parse-representations [{:content-type #{"text/plain" "text/html"}
                                   :charset #{"UTF-8" "Latin-1"}}]))
    identity := #{:content-type :charset})

  (testing "method guards"
      (given
        (vary :post
              (parse-representations [{:method #{:post}
                                       :content-type #{"application/json"}}
                                      {:method #{:get}
                                       :content-type #{"text/plain" "text/html"}
                                       :charset #{"UTF-8" "Latin-1"}}]))
        identity := nil)
    (given
        (vary :get
              (parse-representations [{:content-type #{"application/json"}}
                                      {:method #{:get}
                                       :content-type #{"text/plain"}
                                       :charset #{"UTF-8"}}]))
        identity := #{:content-type})))

(defn parse-csv [s]
  (is s)
  (when s
    (set (str/split s #"\s*,\s*"))))

(st/deftest vary-header-test []
  (let [resource "Hello World!"
        handler (yada/resource resource :produces "text/plain")
        request (request :head "/")
        response @(handler request)]
    (given response
      :status := 200
      [:headers "vary"] :? some?
      [:headers "vary" parse-csv] := #{"accept-charset"})))
