;; Copyright © 2015, JUXT LTD.

(ns yada.parameters-test
  (:require
   [clojure.test :refer :all]
   [schema.core :as s]
   [yada.interceptors :as i]
   [yada.resource :as r]))

(deftest header-test []
  (let [resource (r/resource {:parameters {:header {(s/required-key "X-Foo") s/Str
                                                    }}
                              :methods {}})]
    (let [ctx (i/parse-parameters {:resource resource
                                   :request {:headers {"X-Foo" "Bar"}}})]
      (is (= "Bar"
             (get-in ctx [:parameters :header "X-Foo"]) )))))
