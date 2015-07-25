;; Copyright © 2015, JUXT LTD.

(ns yada.options-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [yada.yada :as yada]
   [juxt.iota :refer (given)]))

;; TODO: OPTIONS needs to be documented and implemented, then re-enable
;; these tests

(deftest options-test []
  #_(testing "Server capabilities"
      (let [resource "Hello World!"
            handler (yada resource)
            request (request :options "*")
            response @(handler request)]
        (given response
          :status := 200)))

  #_(testing "Resource capabilities"
    (let [resource "Hello World!"
          handler (yada resource)
          request (request :options "/")
          response @(handler request)]
      (given response
        :status := 200
        :headers := {"accept" "(TODO)"}))))
