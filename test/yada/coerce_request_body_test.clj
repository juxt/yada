(ns yada.coerce-request-body-test
  (:require [clojure.test :refer :all]
            [yada.body :refer [coerce-request-body]]
            [schema.core :refer [defschema] :as s]))

(s/defschema TestSchema
  {:a s/Str
   :b s/Keyword
   :c [s/Int]})

(def ^:private test-map {:a "Hello" :b :foo :c [4 5 6]})

(defmacro is-coercing-correctly?
  ([expected value content-type schema]
   `(is (= ~expected
           (coerce-request-body ~value ~content-type ~schema))))
  ([expected value content-type]
   `(is (= ~expected
           (coerce-request-body ~value ~content-type)))))

(deftest coerce-request-body-test

  (let [content-type "application/json"
        s "{\"a\": \"Hello\", \"b\": \"foo\", \"c\": [4, 5, 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map s content-type TestSchema)
      (is-coercing-correctly?
        (update test-map :b name)
        s content-type)))

  (let [content-type "application/edn"
        s "{:a \"Hello\" :b :foo :c [4 5 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map s content-type TestSchema)
      (is-coercing-correctly? test-map s content-type))))
