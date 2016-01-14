(ns yada.transit-json-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [byte-streams :as bs]
            [yada.body :as sut]
            [yada.test.util :refer [is-coercing-correctly?]]))

(def ^:private test-map {:a "Hello" :b :foo :c [4 5 6]})

(deftest reading-transit-json-from-body
  (let [content-type "application/transit+json"
        json "[\"^ \",\"~:a\",\"Hello\",\"~:b\",\"~:foo\",\"~:c\",[4,5,6]]"
        json-verbose "{\"~:a\":\"Hello\",\"~:b\":\"~:foo\",\"~:c\":[4,5,6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map json content-type)
      (is-coercing-correctly? test-map json-verbose content-type))))

(deftest rendering-to-transit-json
  (let [basic {:media-type {:name       "application/transit+json"
                            :parameters {"pretty" false}}}
        pretty (assoc-in basic [:media-type :parameters "pretty"] true)

        test-map {:a "Hello" :b :foo :c [4 5 6]}
        map-json "[\"^ \",\"~:a\",\"Hello\",\"~:b\",\"~:foo\",\"~:c\",[4,5,6]]"
        map-json-verbose "{\"~:a\":\"Hello\",\"~:b\":\"~:foo\",\"~:c\":[4,5,6]}"]

    (testing "render-map"
      (is (= map-json
             (bs/to-string (sut/render-map test-map basic))))

      (is (= map-json-verbose
             (bs/to-string (sut/render-map test-map pretty)))))

    (testing "render-seq"
      (is (= "[4,5,6]"
             (bs/to-string (sut/render-seq [4 5 6] basic))
             (bs/to-string (sut/render-seq [4 5 6] pretty)))))

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
