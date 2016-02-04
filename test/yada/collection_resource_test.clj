;; Copyright © 2015, JUXT LTD.

(ns yada.collection-resource-test
  (:require
    [clojure.set :as set]
    [clojure.test :refer :all]
    [clojure.tools.logging :refer :all]
    [clojure.pprint :refer [pprint]]
    [ring.mock.request :as mock]
    [clojure.java.io :as io]
    [clj-time.core :as time]
    [clj-time.coerce :refer [to-date]]
    [ring.util.time :refer (parse-date format-date)]
    [yada.representation :as rep]
    [yada.test.util :refer (to-string)]
    [yada.yada :refer [yada]]
    [clojure.edn :as edn]))

(defn yesterday []
  (time/minus (time/now) (time/days 1)))

(deftest map-resource-test
  (testing "map"
    (let [test-map {:name "Frank"}
          handler (time/do-at (yesterday) (yada test-map))
          request (mock/request :get "/")
          response @(handler request)
          last-modified (some-> response :headers (get "last-modified") parse-date)]

      (is last-modified)
      (is (instance? java.util.Date last-modified))

      (is (= 200 (:status response)))
      (is (= {"content-type" "application/edn"
              "content-length" (count (prn-str test-map))}
             (select-keys (:headers response) ["content-type" "content-length"])))
      (is (instance? java.nio.HeapByteBuffer (:body response)))
      (is (= test-map
             (-> response :body to-string edn/read-string)))

      (let [request (merge (mock/request :get "/")
                           {:headers {"if-modified-since" (format-date (to-date (time/now)))}})
            response @(handler request)]
        (is (= 304 (:status response)))))))
