;; Copyright © 2014-2019, JUXT LTD.

(ns yada.authentication-test
  (:require
   [clojure.test :refer :all]
   [yada.yada :as yada]))

(yada/response-for
 {:authentication
  {:scheme "Basic"
   :realm "Subscriber Area"
   :authenticate (fn [ctx [user password]]
                   ;; Can return ctx
                   ;; nil means no creds established
                   ;; non-ctx value means add to :authentication
                   ;; TODO: test for each of these 3 possibilities.
                   (when (= [user password] ["alice" "wonderland"])
                     {:user "alice"}))}

  :authorization (fn [ctx] true)

  :methods {:get {:produces {:media-type "application/edn"}
                  :response (fn [ctx] (select-keys ctx [:authentication]))}}})

(deftest simple-test

  )
