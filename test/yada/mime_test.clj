(ns yada.mime-test
  (:refer-clojure :exclude [type])
  (:require [yada.mime :refer :all]
            [clojure.test :refer :all]
            [yada.test.util :refer (given)]))

(deftest media-type-test
  (are [x y] (= (into {} (string->media-type x)) y)
    "text/html" {:type "text" :subtype "html" :parameters {} :weight 1.0}
    "text/html;charset=utf-8" {:type "text" :subtype "html" :parameters {"charset" "utf-8"} :weight 1.0}
    "text/html;charset=utf-8;foo=bar" {:type "text" :subtype "html"
                                       :parameters {"charset" "utf-8"
                                                    "foo" "bar"}
                                       :weight 1.0}
    "image/png;q=0.5" {:type "image" :subtype "png" :parameters {} :weight 0.5}))
