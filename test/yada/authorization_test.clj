;; Copyright © 2015, JUXT LTD.

(ns yada.authorization-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [ring.mock.request :refer [request header]]
   [yada.schema :refer :all]
   [schema.core :as s]
   [yada.boolean :as b]
   [yada.authorization :refer [allowed?]]))

(deftest schema-test
  (is
   (nil?
    (s/check b/BooleanExpression [:and true false true]))))

(deftest composite-boolean-logic-test
  (let [a? (fn [pred] (allowed? pred nil nil))]
    (testing "identity"
      (is (true? (a? [:and])))
      (is (not (true? (a? [:or])))))
    (is (not (true? (a? [:and true false true]))))
    (is (true? (a? [:and true true])))
    (is (not (true? (a? [:or false false false]))))
    (is (true? (a? [:or false true])))
    (is (true? (a? [:not false])))
    (is (not (true? (a? [:not true]))))
    (is (true? (a? [:and [:or true false] [:and true true]])))
    (is (not (true? (a? [:and [:or false false] [:and true true]]))))))



