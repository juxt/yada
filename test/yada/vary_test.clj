;; Copyright © 2014-2017, JUXT LTD.

(ns yada.vary-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer [request]]
   [schema.test :as st]
   [yada.representation :refer [vary]]
   [yada.schema :as ys]
   [yada.util :refer [parse-csv]]
   [yada.yada :refer [handler as-resource]]
   yada.resources.string-resource))

(st/deftest vary-test
  (is (= #{:media-type}
         (vary
          (ys/representation-seq
           (ys/representation-set-coercer
            [{:media-type #{"text/plain" "text/html"}}])))))

  (is (= #{:charset}
         (vary
          (ys/representation-seq
           (ys/representation-set-coercer
            [{:media-type "text/html" :charset #{"UTF-8" "Latin-1"}}])))))

  (is (= #{:media-type :charset}
         (vary
          (ys/representation-seq
           (ys/representation-set-coercer
            [{:media-type #{"text/plain" "text/html"}
              :charset #{"UTF-8" "Latin-1"}}]))))))

(st/deftest vary-header-test []
  (let [resource "Hello World!"
        h (handler (merge (as-resource resource)
                          {:produces #{"text/plain" "text/html"}}))
        request (request :head "/")
        response @(h request)
        headers (:headers response)]
    (is (= 200 (:status response)))
    (is (some? (get headers "vary")))
    (is (= #{"accept"} (set (parse-csv (get headers "vary")))))))
