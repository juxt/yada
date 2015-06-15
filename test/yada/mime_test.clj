(ns yada.mime-test
  (:refer-clojure :exclude [type])
  (:require [yada.mime :refer :all]
            [clojure.test :refer :all]
            [yada.test.util :refer (given)]))

(deftest media-type-test
  (are [x y] (= (into {} (string->media-type x)) y)
    "text/html" {:type "text" :subtype "html" :parameters {}}
    "text/html;charset=utf-8" {:type "text" :subtype "html" :parameters {"charset" "utf-8"}}
    "text/html;charset=utf-8;foo=bar" {:type "text" :subtype "html"
                                       :parameters {"charset" "utf-8"
                                                    "foo" "bar"}}))
