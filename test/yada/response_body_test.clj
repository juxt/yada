;; Copyright © 2014-2017, JUXT LTD.

(ns yada.response-body-test
  (:require
   [byte-streams :as b]
   [clojure.test :refer :all]
   [yada.test :refer [response-for]]))

(deftest byte-array-bodies-test []
  (let [response
        (response-for
         {:methods
          {:get
           {:produces "application/vnd.ms-excel"
            :response (fn [ctx]
                        (let [ba (b/to-byte-array "hi")]
                          ba))}}})]
    (is (= 200 (:status response) ))
    (is (= (str (count "hi")) (get-in response [:headers "content-length"]) ))))

;; TODO: Test various body coercions
