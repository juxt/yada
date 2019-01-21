(ns yada.deprecated.schema-test
  (:require
   [clojure.test :refer :all]
   [schema.coerce :as sc]
   [yada.schema :refer :all]))

(deftest authentication-test
  (let [coerce (sc/coercer AccessControl AccessControlMappings)]
    (testing "coerce single realm shorthand to canonical form"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]
                                   }}
               :allow-origin #{"*"}}}
             (coerce
              {:access-control {:realm "default"
                                :authentication-schemes [{:scheme "Basic"
                                                          :verify identity}]

                                :allow-origin "*"}}))))

    (testing "coerce single scheme shorthand to canonical form"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]}}}}
             (coerce
              {:access-control {:realms {"default"
                                         {:scheme "Basic"
                                          :verify identity}}}}))))

    (testing "both shorthand composed"
      (is (= {:access-control
              {:realms {"default" {:authentication-schemes [{:scheme "Basic"
                                                             :verify identity}]}}}}
             (coerce {:access-control {:realm "default"
                                       :scheme "Basic"
                                       :verify identity}}))))))
