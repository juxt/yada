(ns yada.statii-test
  (:require
   [clojure.test :refer :all]
   [yada.util :refer [get*]]))

(deftest get*-test
  (let [m {400 :a
           #{401 404} :b
           * :c}]
    (testing "direct"
      (is (= :a (get* m 400))))
    (testing "via set"
      (is (= :b (get* m 401)))
      (is (= :b (get* m 404))))
    (testing "wildcard"
      (is (= :c (get* m 405)))))
  (testing "nil"
    (is (nil? (get {400 :a} 401)))))

