;; Copyright © 2015, JUXT LTD.

(ns yada.error-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]
   [yada.yada :as yada]
   [ring.mock.request :refer [request]]))

(deftest error-test
  (let [resource (yada/resource
                  (fn [ctx] (throw (ex-info "TODO" {})))
                  {:allowed-methods #{:get}
                   :media-type "text/html"})]
    (given @(resource (request :get "/"))
      :status := 500

      )))
