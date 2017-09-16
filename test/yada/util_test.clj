;; Copyright © 2014-2017, JUXT LTD.

(ns yada.util-test
  (:require
   [clojure.test :refer :all]
   [yada.util :refer (best best-by same-origin?)]))

(deftest best-test
  (is (= (best [3 2 3 nil 19]) 19))
  (is (= (best (comp - compare) [3 2 3 nil 19]) nil)))

(deftest best-by-test
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [nil 0] [19 10]]) [nil 0]))
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [-2 0] [19 10]]) [-2 0])))

(deftest same-origin?-test
  (testing "same origin"
    (testing "no origin header"
      (is (same-origin? {:headers {"host" "localhost:6120"
                                   "referer" "http://localhost:6120/my-app/path/"}
                         :scheme :http})))
    (testing "with origin header"
      (is (same-origin? {:headers {"host" "localhost:6120"
                                   "origin" "http://localhost:6120"
                                   "referer" "http://localhost:6120/my-app/path/"}
                         :scheme :http}))))
  (testing "different origin"
    (testing "different port"
      (is (not (same-origin? {:headers {"host" "localhost:6120"
                                        "referer" "http://localhost:6121/my-app/path/"}
                              :scheme :http}))))
    (testing "different scheme no origin header"
      (is (not (same-origin? {:headers {"host" "localhost:6120"
                                        "referer" "http://localhost:6120/my-app/path/"}
                              :scheme :https}))))
    (testing "different scheme with origin header"
      (is (not (same-origin? {:headers {"host" "localhost:6120"
                                        "origin" "http://localhost:6120"
                                        "referer" "http://localhost:6120/my-app/path/"}
                              :scheme :https}))))
    (testing "different domain"
      (is (not (same-origin? {:headers {"host" "example.com"
                                        "origin" "http://example.net"
                                        "referer" "http://example.net/my-app/path/"}
                              :scheme :http}))))
    (testing "no origin or referer"
      (is (not (same-origin? {:headers {"host" "example.com"}
                              :scheme :http}))))))
