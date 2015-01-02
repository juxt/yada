;; Copyright © 2015, JUXT LTD.

(ns yada.conneg-test
  (:require
   [clojure.test :refer :all]
   [yada.conneg :refer (best-allowed-content-type)]))

(deftest test-content-types
  (is
   (=
    (best-allowed-content-type "text/html" ["text/plain" "text/html"])
    ["text" "html"])))
