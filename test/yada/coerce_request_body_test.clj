(ns yada.coerce-request-body-test
  (:require [clojure.test :refer :all]
            [yada.body :refer [coerce-request-body]]
            [schema.core :refer [defschema] :as s]))

(defschema TestSchema
  {:a s/Str
   :b s/Int})

(deftest coerce-request-body-test

  (testing "coercing application/json"
    (is (= {:a "Hello" :b 123}
           (coerce-request-body
             "{\"a\": \"Hello\", \"b\": 123}"
             "application/json"
             TestSchema)))
    (is (= [1 2 3]
           (coerce-request-body
             "[1, 2, 3]"
             "application/json"))))

  (testing "coercing application/edn"
    (is (= {:a "Hello" :b 123}
           (coerce-request-body
             "{:a \"Hello\" :b 123}"
             "application/edn"
             TestSchema)))
    (is (= [1 2 3]
           (coerce-request-body
             "[1 2 3]"
             "application/edn")))))
