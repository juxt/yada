;; Copyright © 2015, JUXT LTD.

(ns yada.response-body-test
  (:require
   [clojure.test :refer :all]
   [byte-streams :as b]
   [yada.test :refer [response-for]]
   [yada.handler :refer [handler]]
   [yada.resource :refer [resource]]))

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
