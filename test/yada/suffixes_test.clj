;; Copyright © 2014-2019, JUXT LTD.

(ns yada.suffixes-test
  (:require
   [byte-streams :as b]
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [ring.mock.request :as mock]
   [yada.suffixes :refer [fragment-media-type]]
   [yada.handler :refer [handler]]
   [yada.resource :refer [resource as-resource]]))

(deftest suffix-matching-test
  (is (= "application/xml" (fragment-media-type "application/xhtml+xml")))
  (is (= "application/json" (fragment-media-type "application/schema+json")))
  (is (nil? (fragment-media-type "text/html"))))

(deftest produces-suffixes-test
  (let [r (resource
           {:methods
            {:get
             {:produces "application/schema+json"
              :response (fn [ctx] {:message "OK"})}}})

        h (handler r)

        response (-> @(h (mock/request :get "/"))
                     (update :body b/to-string))]

    (is (= 200 (:status response)))
    (is (= (json/encode {:message "OK"}) (str/trim (:body response)))))


  (let [r (resource
           {:methods
            {:get
             {:produces "application/schema+json"
              :response (fn [ctx] ["OK" "this" "is" "working"])}}})

        h (handler r)

        response (-> @(h (mock/request :get "/"))
                     (update :body b/to-string))]

    (is (= 200 (:status response)))
    (is (= (json/encode ["OK" "this" "is" "working"]) (str/trim (:body response))))))
