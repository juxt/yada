;; Copyright © 2015, JUXT LTD.

(ns yada.atom-resource-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [clojure.java.io :as io]
   [juxt.iota :refer (given)]
   [yada.yada :refer [yada]]))

;; TODO: Restore this test once atoms can wrap maps (and other collections)

(deftest atom-test
  #_(testing "atom"
    (let [resource (atom {:name "Frank"})
          handler (yada resource)
          request (request :get "/")
          response @(handler request)]

      (given response
        :status := 200
        :headers :> {"content-length" 16
                     "content-type" "application/edn"}
        :body :? string?)

      )))
