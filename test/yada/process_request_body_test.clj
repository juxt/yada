(ns ^{:author "Imre Koszo"}
    yada.process-request-body-test
    (:require
     [clojure.test :refer :all :exclude [deftest]]
     [cheshire.core :as json]
     [schema.core :as s]
     [schema.test :refer [deftest]]
     [yada.interceptors :as i]
     [yada.resource :refer [resource]]
     [yada.request-body :refer [process-request-body]]
     [yada.test.util :refer [is-coercing-correctly?]]))

(def ^:private test-map {:a "Hello" :b :foo :c [4 5 6]})

(deftest coerce-request-body-test
  (let [content-type "application/json"
        s "{\"a\": \"Hello\", \"b\": \"foo\", \"c\": [4, 5, 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly?
        (update test-map :b name)
        s content-type)))

  (let [content-type "application/edn"
        s "{:a \"Hello\" :b :foo :c [4 5 6]}"]
    (testing (str "coercing " content-type)
      (is-coercing-correctly? test-map s content-type))))

(defn process [body-schema body]
  (let [resource (resource
                  {:methods
                   {:post
                    {:consumes "application/json"
                     :parameters {:body body-schema}
                     :response ""}}})
        ctx {:method :post
             :request {:headers
                       {"content-length" (str (count body))
                        "content-type" "application/json"}
                       :body body}
             :resource resource}]

    (i/process-request-body ctx)))

;; TODO: test 400 errors
;; TODO: transit

(deftest post-parameter-coercion-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process {:foo s/Str}
                            (json/encode {:foo "bar"})
                            ) [:parameters :body]))))
  (testing "sad path"
    (is (thrown? clojure.lang.ExceptionInfo
                 (get-in (process {:foo s/Str}
                                  (json/encode {:foo 123})
                                  ) [:parameters :body])))))
