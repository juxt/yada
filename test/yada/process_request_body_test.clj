(ns ^{:author "Imre Koszo"}
    yada.process-request-body-test
    (:require
     [clojure.test :refer :all :exclude [deftest]]
     [schema.test :refer [deftest]]
     [yada.request-body :refer [process-request-body]]
     [yada.test.util :refer [is-coercing-correctly?]]))

(def ^:private test-map {:a "Hello" :b :foo :c [4 5 6]})

(deftest coerce-request-body-test

  (let [content-type "application/json"
        s "{\"a\": \"Hello\", \"b\": \"foo\", \"c\": [4, 5, 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly?
        (update test-map :b name)
        s content-type)))

  (let [content-type "application/edn"
        s "{:a \"Hello\" :b :foo :c [4 5 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map s content-type))))

