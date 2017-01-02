(ns yada.webjar-resource-test
  (:require [clojure.test :refer [deftest is testing]]
            [yada.resources.webjar-resource :refer [new-webjar-resource]]
            [clojure.java.io :as io]
            [clojure.java.io :as io]
            [yada.test :as test]))

(deftest bootstrap-test
  (let [expected (slurp (io/resource "META-INF/resources/webjars/bootstrap/3.3.6/less/close.less"))]
    (is (= expected
           (:body (test/response-for ["" (new-webjar-resource "bootstrap"
                                                              {:index-files ["close.less"]})]
                                     :get "/less/"))))
    (is (= expected
           (:body (test/response-for ["" (new-webjar-resource "bootstrap")]
                                     :get "/less/close.less"))))))
