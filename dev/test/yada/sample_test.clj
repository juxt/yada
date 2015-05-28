;; Copyright © 2015, JUXT LTD.

(ns yada.sample-test
  (:require
   [clojure.test :refer :all]
   [yada.test :as yada]))


(def my-resource {:body "Hello World!"})

(deftest api-test
  (yada/pummel my-resource
               :trials 2000
               :protocol :http
               ))
