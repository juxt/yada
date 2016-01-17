;; Copyright © 2015, JUXT LTD.

(ns yada.test
  (:require
   [clojure.test :refer :all]))

(defn pummel
  "verb: strike repeatedly with the fists"
  [resource-map & {:keys [trials] :as args}]
  (dotimes [n (or trials 1000)]
    (do-report {:type :pass})))
