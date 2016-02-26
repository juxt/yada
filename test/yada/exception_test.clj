;; Copyright © 2015, JUXT LTD.

(ns yada.exception-test
  (:require
   [clojure.test :refer :all]
   [yada.test :refer [response-for]]
   [yada.handler :refer [handler]]))

(response-for (ex-info "Error" {} (ex-info "cause" {})))

(deftest exception-test
  (let [resp (response-for (ex-info "Error" {} (ex-info "cause" {})))]
    (is (< 1000 (Integer/parseInt (get-in resp [:headers "content-length"])) ))))

