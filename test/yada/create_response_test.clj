;; Copyright © 2014-2017, JUXT LTD.

(ns yada.create-response-test
  (:require
   [clojure.test :refer :all :exclude [deftest]]
   [schema.test :refer [deftest]]
   [yada.interceptors :refer [create-response]]))

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
           ["foo=bar; HttpOnly; Path=/abc"]
           (get-in response [:headers "set-cookie"])))))
  (testing "that same-site cookie attribute works"
    (let [ctx {:response
               {:cookies {"foo" {:value "bar"
                                 :path "/abc"
                                 :same-site :lax}}}}
          response (:response (create-response ctx))]
      (is (=
           ["foo=bar; Path=/abc; SameSite=lax"]
           (get-in response [:headers "set-cookie"])))))
  (testing "that cookies with nil cause an exception"
    (let [ctx {:response
               {:cookies {"bar" "baz"
                          "lol" nil}}}
          ex  (try
                (:response (create-response ctx))
                (catch Exception ex
                  ex))]
      (is (=
           (:type (ex-data ex))
           :coercion)))))
