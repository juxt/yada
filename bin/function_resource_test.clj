;; Copyright © 2015, JUXT LTD.

(ns yada.function-resource-test
  (:require
   [byte-streams :as b]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clj-time.core :as time]
   [clj-time.coerce :refer (to-date)]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (format-date)]
   [yada.yada :as yada :refer [yada]]))

(deftest function-test
  (testing "Producing a Java string implies utf-8 charset"
    (let [handler (yada (fn [ctx] "Hello World!"))
          request (request :get "/")
          response @(handler request)]
      (is (= 200 (:status response)))
      (is (= "Hello World!" (b/to-string (:body response)))))))
