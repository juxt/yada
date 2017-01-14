;; Copyright © 2014-2017, JUXT LTD.

(ns yada.charset-test
  (:require
   [clojure.test :refer :all]
   [yada.charset :as ch]))

(deftest no-charset-appears-twice-in-platform-charsets
  (let [charset-aliases (map :alias ch/platform-charsets)]
    (is (= (sort charset-aliases)
           (sort (distinct charset-aliases))))))

(deftest a-single-charset-is-the-most-preferred
  (is (= 1
         (->> ch/platform-charsets
              (map :quality)
              (filter (partial = 1.0))
              count))))

(deftest most-preferred-charset
  (let [most-preferred-charset (->> ch/platform-charsets
                                    (sort-by :quality)
                                    last)
        top-quality            1.0]

    (testing "has :quality 1.0"
      (is (= top-quality (:quality most-preferred-charset))))

    (testing "is the only one with 1.0 quality"
      (is (= 1 (->> (map :quality ch/platform-charsets)
                    (filter (partial = top-quality))
                    count))))

    (testing "is utf-8 or the platform default charset when utf-8 is not available"
      (let [utf-8-or-default (or (-> (java.nio.charset.Charset/availableCharsets)
                                     keys
                                     set
                                     (get (.name (java.nio.charset.StandardCharsets/UTF_8))))
                                 (.name (java.nio.charset.Charset/defaultCharset)))]
        (is (= utf-8-or-default
               (:alias most-preferred-charset)))))))
