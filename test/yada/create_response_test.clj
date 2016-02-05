;; Copyright © 2015, JUXT LTD.

(ns yada.create-response-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [yada.interceptors :refer [create-response]]
   [yada.schema :as ys]))

(deftest cookies-test []
  (testing "that simple values work"
    (let [ctx {:response {:cookies {"foo" "bar"}}}
          response (:response (create-response ctx))]
      (is (= ["foo=bar"]
             (get-in response [:headers "set-cookie"])))))
  (testing "that cookie attribute are formatted properly"
    (let [ctx {:response
               {:cookies {"foo" {:value "bar"
                                 :path "/abc"
                                 :http-only true}}}}
          response (:response (create-response ctx))]
      (is (=
           ["foo=bar; Path=/abc; HttpOnly"]
           (get-in response [:headers "set-cookie"]))))))




