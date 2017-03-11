(ns yada.request-for-test
  (:require [clojure.test :refer [deftest is testing]]
            [yada.test]))

(deftest request-for-query-params
  (testing "make sure we're not double-decoding"
    (is (= (-> {:request (yada.test/request-for :get "/?foo=bar%20" nil)}
               (yada.parameters/query-parameters))
           {"foo" "bar "}))
    (is (= (-> {:request (yada.test/request-for :get "/?foo=bar%25" nil)}
               (yada.parameters/query-parameters))
           {"foo" "bar%"})))

  (testing "no decoding should be done in request-for"
    (is (= (:query-string (yada.test/request-for :get "/?foo=bar%20" nil))
           "foo=bar%20"))
    (is (= (:query-string (yada.test/request-for :get "/?foo=bar%25" nil))
           "foo=bar%25"))))

