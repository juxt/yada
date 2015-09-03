;; Copyright © 2015, JUXT LTD.

(ns yada.access-control-test
  (:require
   [clojure.test :refer :all]
   [yada.test.util :refer (to-string)]
   [juxt.iota :refer [given]]
   [yada.yada :refer [yada]]
   [yada.methods :refer [Get]]
   [yada.protocols :as p]
   [ring.mock.request :refer [request header]]))


;; This test addresses access control, for protecting resources from
;; access by unauthorized user-agents (authorization) and unauthorized
;; scripts (access control).

(request :options "/")

(defrecord OpenResource []
  )

(defrecord SecureResource [user password]

  Get
  (GET [_ ctx] "Hello Friend!")
  )

#_(deftest authorization-test []
  (testing "Secure resource"
    (let [resource (map->SecureResource {})
          handler (yada resource)
          request (request :get "/")
          response @(handler request)]
      (given response
        :status := 401
        [:body to-string] := "Hello Friend!"))))



;; If the resource is secured, yet open, check we can call OPTIONS
;; without requiring an Authorization header
