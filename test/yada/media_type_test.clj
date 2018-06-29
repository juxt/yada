;; Copyright © 2014-2017, JUXT LTD.

(ns yada.media-type-test
  (:refer-clojure :exclude [type])
  (:require
   [clojure.test :refer :all]
   [yada.media-type :refer :all]))

(deftest string->media-type-test
  (testing "Invalid values return nil"
    (are [x] (nil? (string->media-type x))
      nil "" ";" "junk" "application/transit+json;, application/json;q=0.6"))
  (testing "Valid values are correctly parsed"
    (let [result {:name "text/html" :type "text" :subtype "html"
                  :parameters {"charset" "utf-8" "foo" "bar"}
                  :quality 1.0}]
      (are [x y] (= (into {} (string->media-type x)) y)
        "text/html" {:name "text/html" :type "text" :subtype "html" :parameters {} :quality 1.0}
        "text/html;charset=utf-8" {:name "text/html" :type "text" :subtype "html" :parameters {"charset" "utf-8"} :quality 1.0}
        "text/html;charset=utf-8;foo=bar" result
        "text/html; charset=utf-8;foo=bar" result
        "text/html; charset=utf-8;   \tfoo=bar" result
        "image/png;q=0.5" {:name "image/png" :type "image" :subtype "png" :parameters {} :quality 0.5}
        "*/*" {:name "*/*" :type "*" :subtype "*" :parameters {} :quality 1.0}
        "*" {:name "*/*" :type "*" :subtype "*" :parameters {} :quality 1.0}
        "*; charset=utf-8; q=0.5" {:name "*/*" :type "*" :subtype "*" :parameters {"charset" "utf-8"} :quality 0.5}))))
