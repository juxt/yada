;; Copyright © 2014-2017, JUXT LTD.

;; This is an old test against swagger parameters which should work
;; against the new non-swagger implementation (I can't see why not
;; right now).

(ns yada.parameters-2-test
  (:require
   [clojure.test :refer :all]
   [schema.core :as s]
   [yada.parameters :refer [parse-parameters]]
   [yada.resource :as r])
  (:import [clojure.lang ExceptionInfo IDeref]))

(deftest header-test []
  (let [resource (r/resource
                  {:parameters {:header {(s/required-key "X-Foo") s/Str}}
                   :methods {}})]
    (let [ctx (parse-parameters
               {:resource resource
                :request {:headers {"X-Foo" "Bar"}}})]
      (is (= "Bar"
             (get-in ctx [:parameters :header "X-Foo"]) )))))

(defn parse [resource request]
  (let [ret (parse-parameters {:resource (r/resource resource)
                               :request request})]
    (if (instance? IDeref ret)
      @ret
      (:parameters ret))))

(deftest query-test
  (testing "keyword keys"
    (is (= {:query {:filter 12}}
           (parse {:parameters {:query {:filter Long}}
                   :methods {}}
                  {:query-string "filter=12"})))
    (is (= {:query {:filter "12"}}
           (parse {:parameters {:query {:filter s/Str}}
                   :methods {}}
                  {:query-string "filter=12"})))
    (is (= {:query {:filter "12"}}
           (parse {:parameters
                   {:query
                    {:filter s/Str
                     (s/optional-key :test) Long}}
                   :methods {}}
                  {:query-string "filter=12"})))
    (is (= {:query {:filter "12"
                    :test 15}}
           (parse {:parameters {:query {:filter s/Str
                                        (s/optional-key :test) Long}}
                   :methods {}}
                  {:query-string "filter=12&test=15"}))))
  (testing "string keys"
    (is (= {:query {"filter" 12}}
           (parse {:parameters {:query {(s/required-key "filter") Long}}
                   :methods {}}
                  {:query-string "filter=12"})))
    (is (= {:query {"filter" "12"}}
           (parse {:parameters {:query {(s/required-key "filter") s/Str
                                        (s/optional-key "test") Long}}
                   :methods {}}
                  {:query-string "filter=12"})))
    (is (= {:query {"filter" "12"
                    "test" 15}}
           (parse {:parameters
                   {:query {(s/required-key "filter") s/Str
                            (s/optional-key "test") Long}}
                   :methods {}}
                  {:query-string "filter=12&test=15"}))))
  (is (thrown? ExceptionInfo
               (parse {:parameters {:query {:filter Long}}
                       :methods {}}
                      {:query-string "filter=test"})))
  (testing "schema key"
    (is (= {:query {:filter "test"
                    :order-direction :asc}}
           (parse {:parameters
                   {:query {:filter s/Str
                            :order-direction (s/enum :asc :desc)
                            s/Keyword Long}}
                   :methods {}}
                  {:query-string "filter=test&order-direction=asc"})))
    (is (= {:query {:filter "test"
                    :order-direction :asc
                    "age" "25"}}
           (parse {:parameters
                   {:query
                    {:filter s/Str
                     :order-direction (s/enum :asc :desc)
                     s/Any s/Any}}
                   :methods    {}}
                  {:query-string "filter=test&order-direction=asc&age=25"})))
    (is (= {:query {:filter "test"
                    :order-direction :asc}}
           (parse {:parameters
                   {:query
                    {:filter s/Str
                     (s/optional-key :order-direction) (s/enum :asc :desc)
                     s/Keyword Long}}
                   :methods {}}
                  {:query-string "filter=test&order-direction=asc"})))
    (is (= {:query {:filter "test"
                    :order-direction :asc
                    :age 12}}
           (parse {:parameters
                   {:query
                    {:filter s/Str
                     (s/optional-key :order-direction) (s/enum :asc :desc)
                     s/Keyword Long}}
                   :methods {}}
                  {:query-string "filter=test&order-direction=asc&age=12"})))
    (is (= {:query {:filter "test"
                    :age    12}}
           (parse {:parameters
                   {:query
                    {:filter s/Str
                     (s/optional-key :order-direction) (s/enum :asc :desc)
                     s/Keyword Long}}
                   :methods {}}
                  {:query-string "filter=test&age=12"})))
    (is (= {:query {14 12
                    17 88
                    :filter "test"}}
           (parse {:parameters
                   {:query {:filter s/Str
                            Long Long}}
                   :methods {}}
                  {:query-string "14=12&filter=test&17=88"})))
    (is (= {:query {:filter "test"}}
           (parse {:parameters {:query {:filter s/Str
                                        Long Long}}
                   :methods {}}
                  {:query-string "filter=test"})))
    (is (thrown? ExceptionInfo
                 (parse {:parameters
                         {:query {:filter s/Str
                                  Long Long}}
                         :methods {}}
                        {:query-string "14=12"})))
    (is (thrown? ExceptionInfo
                 (parse {:parameters
                         {:query {:filter s/Str
                                  Long Long}}
                         :methods {}}
                        {:query-string "filter=test&muh=12"})))))
