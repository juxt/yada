;; Copyright © 2014-2017, JUXT LTD.

(ns yada.cookies-coercion-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [yada.cookies :refer [cookies-coercer]]
   ))

;; Based on an original recipe ring.middleware.cookies, my own
;; includes chocolate-chip coercions.

(deftest coercion-test
  (testing "that string values are coerced to a cookie value map"
    (is (= {"foo" {:value "bar"}}
           (cookies-coercer {"foo" "bar"}))))
  (testing "that dates are get formatted to RFC 822 date-strings"
    (is (= {"foo" {:value "bar"
                   :expires #inst "2001-09-09T01:46:40.000-00:00"}}
           (cookies-coercer {"foo" {:value "bar"
                                    :expires (java.util.Date. 1000000000000)}}))))
  (testing "full cookie"
    (let [cookie {"foo" {:value "bar",
                         :max-age 0
                         :expires "Sun, 09 Sep 2001 01:46:40 GMT",
                         :domain "juxt.pro",
                         :path "/",
                         :secure true,
                         :http-only true}}
          cookie-max-age-str (assoc-in cookie ["foo" :max-age] "0")]
      (is (= cookie
             (cookies-coercer cookie)))
      (is (= cookie-max-age-str
             (cookies-coercer cookie-max-age-str))))))

(comment
  (coercion-test))
