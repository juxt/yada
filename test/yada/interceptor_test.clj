;; Copyright © 2014-2017, JUXT LTD.

(ns yada.interceptor-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [yada.handler
    :refer
    [append-interceptor insert-interceptor prepend-interceptor]]))

(deftest prepend-interceptor-test
  (let [res {:interceptor-chain [:b :c :d :e]}]
    (is
     (=
      [:a1 :a2 :b :c :d :e]
      (:interceptor-chain (prepend-interceptor res :a1 :a2))))))

(deftest insert-interceptor-test
  (let [res {:interceptor-chain [:b :c :d :e]}]
    (are [f expected]
        (= expected (:interceptor-chain f))
      (insert-interceptor res :c :b1 :b2) [:b :b1 :b2 :c :d :e]
      (insert-interceptor res :b :a1 :a2) [:a1 :a2 :b :c :d :e]
      (insert-interceptor res :z :a1 :a2) [:b :c :d :e])))

(deftest append-interceptor-test
  (let [res {:interceptor-chain [:b :c :d :e]}]
    (are [f expected]
        (= expected (:interceptor-chain f))
      (append-interceptor res :c :c1 :c2) [:b :c :c1 :c2 :d :e]
      (append-interceptor res :z :d1 :d2) [:b :c :d :e])))
