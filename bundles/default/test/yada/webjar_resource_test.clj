;; Copyright © 2014-2017, JUXT LTD.

(ns yada.webjar-resource-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is]]
   [yada.resources.webjar-resource :refer [new-webjar-resource webjars-route-pair]]
   [yada.test :as test]))

(deftest bootstrap-test
  (let [expected (slurp (io/resource "META-INF/resources/webjars/bootstrap/3.3.6/less/close.less"))]
    (is (= expected
           (:body (test/response-for ["" (new-webjar-resource "bootstrap"
                                                              {:index-files ["close.less"]})]
                                     :get "/less/"))))
    (is (= expected
           (:body (test/response-for ["" (new-webjar-resource "bootstrap")]
                                     :get "/less/close.less"))))
    (is (= 404
           (:status (test/response-for ["" (new-webjar-resource "bootstrap")]
                                       :get "/non/existant/path"))))
    (is (= expected
           (:body (test/response-for (webjars-route-pair)
                                     :get "/bootstrap/less/close.less"))))
    (is (= 404
           (:status (test/response-for (webjars-route-pair)
                                       :get "/bootstrap/non/existant/path"))))))
