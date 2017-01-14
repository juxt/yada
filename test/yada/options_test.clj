;; Copyright © 2014-2017, JUXT LTD.

(ns yada.options-test
  (:require
   [clojure.test :refer :all]))

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
