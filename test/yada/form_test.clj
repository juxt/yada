(ns yada.form-test
  (:require
   [clojure.tools.logging :refer :all]
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [byte-streams :as bs]
   [juxt.iota :refer (given)]
   [yada.test.util :refer [to-manifold-stream]]
   [ring.mock.request :as mock]
   [ring.util.codec :as codec]
   [schema.core :as s]
   [yada.resources.misc :refer (just-methods)]
   [yada.yada :as yada :refer [yada]]))

(deftest post-test
  (let [handler (yada
                 (just-methods
                  :post {:parameters {:form {(s/required-key "foo") s/Str}}
                         :response (fn [ctx]
                                     (pr-str (:parameters ctx)))}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {}))

    ;; Form post body
    (let [response (handler (-> (mock/request :post "/"
                                              {"foo" "bar"})
                                ;; TODO: If we don't convert to a
                                ;; manifold stream the test hangs, find
                                ;; out why this is and cope with it
                                ;; better.
                                (update :body to-manifold-stream)))]
      @response
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {:form {"foo" "bar"}}))))

#_(deftest post-test-with-query-params
  (let [handler (yada
                 (just-methods
                  :post {:parameters {:query {(s/required-key "foo") s/Str}
                                      :form {(s/required-key "bar") s/Str}}
                         :response (fn [ctx] (pr-str (:parameters ctx)))}))]

    ;; Nil post body
    (let [response (handler (mock/request :post "/?foo=123"))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {"foo" "123"
                                                 :query {"foo" "123"}}))

    ;; Form post body
    (let [response (handler (mock/request :post "/?foo=123"
                                          {"bar" "456"}))]
      (given @response
        :status := 200
        [:body bs/to-string edn/read-string] := {"foo" "123" "bar" "456"
                                                 :query {"foo" "123"}
                                                 :form {"bar" "456"}}))))
