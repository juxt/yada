;; Copyright © 2015, JUXT LTD.

(ns yada.media-type-test
  (:refer-clojure :exclude [type])
  (:require [yada.media-type :refer :all]
            [clojure.test :refer :all]
            [juxt.iota :refer (given)]))

(deftest media-type-test
  (are [x y] (= (into {} (string->media-type x)) y)
    "text/html" {:type "text" :subtype "html" :parameters {} :quality 1.0}
    "text/html;charset=utf-8" {:type "text" :subtype "html" :parameters {"charset" "utf-8"} :quality 1.0}
    "text/html;charset=utf-8;foo=bar" {:type "text" :subtype "html"
                                       :parameters {"charset" "utf-8"
                                                    "foo" "bar"}
                                       :quality 1.0}
    "image/png;q=0.5" {:type "image" :subtype "png" :parameters {} :quality 0.5}))
