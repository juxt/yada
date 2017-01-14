;; Copyright © 2014-2017, JUXT LTD.

(ns yada.cookies-coercion-test
  (:require
   [yada.cookies :refer [cookies-coercer]]
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]))

;; Based on an original recipe ring.middleware.cookies, my own
;; includes chocolate-chip coercions.

(deftest coercion-test
  (testing "that string values are coerced to a cookie value map"
    (is (= {"foo" {:value "bar"}}
           (cookies-coercer {"foo" "bar"}))))
  (testing "that dates are get formatted to RFC 822 date-strings"
    (is (= {"foo" {:value "bar"
                   :expires "Sun, 09 Sep 2001 01:46:40 +0000"}}
           (cookies-coercer {"foo" {:value "bar"
                                   :expires (java.util.Date. 1000000000000)}}))))
  (testing "full cookie"
    (is (= {"foo" {:value "bar",
                   :expires "Sun, 09 Sep 2001 01:46:40 +0000",
                   :domain "juxt.pro",
                   :path "/",
                   :secure true,
                   :http-only true}}
           (cookies-coercer {"foo" {:value "bar"
                                   :expires (java.util.Date. 1000000000000)
                                   :domain "juxt.pro"
                                   :path "/"
                                   :secure true
                                    :http-only true}})))))



