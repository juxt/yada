(ns yada.test.util-test
  (:require
   [yada.test.util :refer :all]
   [clojure.test :refer :all]))

(deftest myself
  (from :foo
    keyword? true
    identity :foo
    [str count] 4
    ))
