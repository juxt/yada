(ns yada.form-test
  (:require
   [clojure.tools.logging :refer :all]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [byte-streams :as bs]
   [juxt.iota :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.yada :as yada :refer [yada]]
   [yada.resource :refer [resource]]))


(deftest post-test
  (let [handler (yada
                 (resource
                  {:methods
                   {:post {:parameters {:form {:foo s/Str}}
                           :consumes "application/x-www-form-urlencoded"
                           :handler (fn [ctx]
                                      (pr-str (:parameters ctx)))}}}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/"))]
      (given @response
        :status := 200
        [:body edn/read-string] := {}))

    ;; Form post body
    (let [response (handler (mock/request :post "/"
                                          {"foo" "bar"}))]
      @response
      (given @response
        :status := 200
        [:body edn/read-string] := {:form {:foo "bar"}}
        ))))

;; Need to test where strings are used rather than keywords

(deftest post-test-with-query-params
  (let [handler (yada
                 (resource
                  {:methods
                   {:post {:parameters {:query {:foo s/Str}
                                        :form {:bar s/Str}}
                           :consumes "application/x-www-form-urlencoded"
                           :handler (fn [ctx] (pr-str (:parameters ctx)))}}}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/?foo=123"))]
      (given @response
        :status := 200
        [:body edn/read-string] := {:query {:foo "123"}}))

    ;; Form post body
    (let [response (handler (mock/request :post "/?foo=123"
                                          {"bar" "456"}))]
      (given @response
        :status := 200
        [:body edn/read-string] := {:query {:foo "123"}
                                    :form {:bar "456"}}))))
