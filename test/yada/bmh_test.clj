;; Copyright © 2016, JUXT LTD.

(ns yada.bmh-test
  (:require
   [byte-streams :as b]
   [clojure.test :refer :all]
   [yada.bmh :refer :all]))

(deftest bmh-test
  (is (= [12 41 51]
         (search (b/to-byte-array "roadrunner")
                 (b/to-byte-array "i7PK9k+ZfWH6roadrunnerWVr5yuhgTYhMsUzhi8+roadrunnerroadrunner629Ez0XuS2Do+KIvd")))))


