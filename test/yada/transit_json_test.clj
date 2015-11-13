(ns yada.transit-json-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [yada.body :as sut]))

(s/defschema TestSchema
  {:a s/Str
   :b s/Keyword
   :c [s/Int]})

(def ^:private test-map {:a "Hello" :b :foo :c [4 5 6]})

(defmacro is-coercing-correctly?
  ([expected value content-type schema]
   `(is (= ~expected
           (sut/coerce-request-body ~value ~content-type ~schema))))
  ([expected value content-type]
   `(is (= ~expected
           (sut/coerce-request-body ~value ~content-type)))))

(deftest reading-transit-json-from-body
  (let [content-type "application/transit+json"
        json "[\"^ \",\"~:a\",\"Hello\",\"~:b\",\"~:foo\",\"~:c\",[4,5,6]]"
        json-verbose "{\"~:a\":\"Hello\",\"~:b\":\"~:foo\",\"~:c\":[4,5,6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map json content-type)
      (is-coercing-correctly? test-map json content-type TestSchema)
      (is-coercing-correctly? test-map json-verbose content-type)
      (is-coercing-correctly? test-map json-verbose content-type TestSchema))))

(deftest rendering-to-transit-json
  (let [basic {:media-type {:name       "application/transit+json"
                            :parameters {"pretty" false}}}
        pretty (assoc-in basic [:media-type :parameters "pretty"] true)
        
        test-map {:a "Hello" :b :foo :c [4 5 6]}
        map-json "[\"^ \",\"~:a\",\"Hello\",\"~:b\",\"~:foo\",\"~:c\",[4,5,6]]"
        map-json-verbose "{\"~:a\":\"Hello\",\"~:b\":\"~:foo\",\"~:c\":[4,5,6]}"]

    (testing "render-map"
      (is (= map-json
             (sut/render-map test-map basic)))

      (is (= map-json-verbose
             (sut/render-map test-map pretty))))

    (testing "render-seq"
      (is (= "[4,5,6]"
             (sut/render-seq [4 5 6] basic)
             (sut/render-seq [4 5 6] pretty))))

    (testing "render-error"
      (is (= {:status  503
              :message "Service Unavailable"
              :id      12345
              :error   {:some :error-happened}}

             (sut/render-error 503
                               {:some :error-happened}
                               basic
                               {:id 12345})

             (sut/render-error 503
                               {:some :error-happened}
                               pretty
                               {:id 12345})

             (sut/render-error 503
                               {:some :error-happened}
                               (assoc-in basic [:media-type :name] "application/json")
                               {:id 12345}))))
    ))
