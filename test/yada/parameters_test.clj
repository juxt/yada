(ns yada.parameters-test
  (:require
   [byte-streams :as bs]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [yada.yada :refer (yada)]
   [yada.resource :as res]
   [yada.test.util :refer (given)]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.resources.misc :refer (just-methods)]
   [clojure.tools.logging :refer :all]))

(deftest post-test
  (let [handler (yada (just-methods
                       :post {:function
                              (fn [ctx]
                                (pr-str (:parameters ctx)))
                              :parameters {:form {:foo s/Str}}}))]

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
  (let [handler (yada (just-methods
                       :post {:function
                              (fn [ctx] (pr-str (:parameters ctx)))
                              :parameters {:query {:foo s/Str}
                                          :form {:bar s/Str}}}))]

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
