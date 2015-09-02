;; Copyright © 2015, JUXT LTD.

(ns yada.error-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]
   [yada.yada :as yada :refer [yada]]
   [ring.mock.request :refer [request]]))

(deftest error-test
  (let [journal (atom {})
        errors (atom [])
        resource (yada
                  (fn [ctx] (throw (ex-info "Problem!" {:data 123})))
                  {:allowed-methods #{:get}
                   :media-type "text/html"
                   :journal journal
                   :error-handler (fn [e] (swap! errors conj e))})]
    (given @(resource (request :get "/"))
      :status := 500)
    (given @journal
      first :? vector?
      ;;first := nil
      ;;[first second :error :exception :data] := nil
      )
    (given @errors
      count := 1)))
