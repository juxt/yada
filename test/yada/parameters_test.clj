(ns yada.parameters-test
  (:require
   [clojure.tools.logging :refer :all]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [byte-streams :as bs]
   [juxt.iota :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.resources.misc :refer (just-methods)]
   [yada.yada :as yada]))

(deftest post-test
  (let [handler (yada/resource
                 (just-methods
                  :post {:parameters {:form {:foo s/Str}}
                         :response (fn [ctx]
                                     (pr-str (:parameters ctx)))}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := nil
        ))

    ;; Form post body
    (let [response (handler (mock/request :post "/"
                                          {"foo" "bar"}))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "bar"}))))

(deftest post-test-with-query-params
  (let [handler (yada/resource
                 (just-methods
                  :post {:parameters {:query {:foo s/Str} :form {:bar s/Str}}
                         :response (fn [ctx] (pr-str (:parameters ctx)))}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/?foo=123"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "123"}))

    ;; Form post body
    (let [response (handler (mock/request :post "/?foo=123"
                                          {"bar" "456"}))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:foo "123" :bar "456"}))))
