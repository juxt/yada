;; Copyright © 2015, JUXT LTD.

(ns yada.error-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]
   [yada.yada :as yada]
   [ring.mock.request :refer [request]]))

(deftest error-test
  (let [journal (atom {})
        resource (yada/resource
                  (fn [ctx] (throw (ex-info "Problem!" {:data 123})))
                  {:allowed-methods #{:get}
                   :media-type "text/html"
                   :journal journal})]
    (given @(resource (request :get "/"))
      :status := 500)
    (given @journal
      first :? vector?
      [first second :error :data] := {:data 123})))
