;; Copyright © 2014-2017, JUXT LTD.

(ns yada.logging-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [yada.resource :refer [resource]]
   [yada.test :refer [response-for]]))

;; Happy path
(deftest logging-test
  (testing "happy path"
    (let [a (atom nil)
          res (response-for {:produces "text/plain"
                             :response "hi"
                             :logger (fn [ctx] (reset! a :logged!) nil)})]

      (is (= 200 (:status res)))
      (is (= :logged! @a))))

  (testing "sad path"
    (let [a (atom nil)
          res (response-for {:produces "text/plain"
                             :response (fn [_] (throw (new Exception "Whoops!")))
                             :logger (fn [ctx] (reset! a :error) nil)})]

      (is (= 500 (:status res)))
      (is (= :error @a)))))
