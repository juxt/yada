;; Copyright © 2014-2017, JUXT LTD.

(ns yada.statii-test
  (:require
   [clojure.test :refer :all]
   [yada.util :refer [assoc* conj* disjoint*? dissoc* expand get* merge*]]))

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
    (is (nil? (get* {400 :a} 401))))
  (is (= :a (get* {400 :a #{400 401} :b} 400))))

(deftest disjoint*?-test
  (is (= true (disjoint*? {200 :a * :b})))
  (is (= true (disjoint*? {200 :a (set (range 201 209)) :b})))
  (is (= true (disjoint*? {#{200 202} :a #{201 203} :b})))
  (is (= false (disjoint*? {200 :a #{200 201} :b})))
  (is (= false (disjoint*? {#{200 201} :a #{201 202} :b}))))

(deftest dissoc*-test
  (let [m {200            :a
           #{400 401 403} :b
           *              :c}]
    (testing "missing"
      (is (= (dissoc* m 201) m))
      (is (= (dissoc* m #{201 203}) m)))
    (testing "wildcard"
      (is (= (dissoc* m *)
             {200 :a #{400 401 403} :b})))
    (testing "simply key"
      (is (= (dissoc* m 200)
             {#{400 401 403} :b * :c}))
      (is (= (dissoc* m 400)
             {200 :a #{401 403} :b * :c})))
    (testing "set key"
      (is (= (dissoc* m #{200 401})
             {#{400 403} :b * :c}))
      (is (= (dissoc* m #{201 401 403})
             {200 :a 400 :b * :c}))
      (is (= (dissoc* m #{200 400 401 403})
             {* :c})))
    (is (= (dissoc* m 200 #{400 401} 403)
           {* :c}))))

(deftest assoc*-test
  (let [m {200            :a
           #{400 401 403} :b
           *              :c}]
    (testing "simple key"
      (is (= (assoc* m 201 :d)
             {200 :a 201 :d #{400 401 403} :b * :c})
          "adds missing simple key")
      (is (= (assoc* m 200 :d)
             {200 :d #{400 401 403} :b * :c})
          "replaces simple key")
      (is (= (assoc* m 400 :d)
             {200 :a #{401 403} :b 400 :d * :c})
          "removes from set key and adds simple key"))
    (is (= (assoc* m * :d)
           {200 :a #{400 401 403} :b * :d}))
    (testing "set key"
      (is (= (assoc* m #{201 203} :d)
             {200 :a #{201 203} :d #{400 401 403} :b * :c}))
      (is (= (assoc* m #{200 401} :d)
             {#{400 403} :b #{200 401} :d * :c}))
      (is (= (assoc* m #{201 401 403} :d)
             {200 :a 400 :b #{201 401 403} :d * :c}))
      (is (= (assoc* m #{200 400 401 403} :d)
             {#{200 400 401 403} :d * :c})))
    (is (= (assoc* m 200 :d #{400 401} :e 403 :f)
           {200 :d #{400 401} :e 403 :f * :c}))))

(deftest conj*-test
  (let [m {200            :a
           #{400 401 403} :b
           *              :c}
        d {200 :a
           201 :d
           #{400 401 403} :b
           * :c}]
    (is (= (conj* m [201 :d]) d))
    (is (= (conj* m (first {201 :d})) d))
    (is (= (conj* m (seq {201 :d})) d))
    (is (= (conj* m {201 :d} {}) d))
    (is (= (conj* m {200 :q #{400 401} :e} [403 :f] [200 :d])
           {200 :d #{400 401} :e 403 :f * :c}))))

(deftest merge*-test
  (is (= (merge* {}
                 {200 :q 401 :m * :c}
                 nil
                 {#{400 401 403} :b 200 :s}
                 {200 :a})
         {200            :a
          #{400 401 403} :b
          *              :c})))

(deftest expand-test
  (is (= {100 :a} (expand {100 :a})))
  (is (= {* :a} (expand {* :a})))
  (is (= {100 :a * :b 201 :c 204 :c 500 :d 404 :e 300 :e 203 :e}
         (expand {100            :a
                  *              :b
                  #{201 204}     :c
                  #{500}         :d
                  #{404 300 203} :e}))))
