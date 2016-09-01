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
    [yada.test-util :refer [is-coercing-correctly?]]
    [byte-streams :as bs]
    [schema.coerce :as sc]
    [cognitect.transit :as transit])
  (:import (clojure.lang ExceptionInfo Keyword)
           (java.io ByteArrayOutputStream)))

(declare thrown?)

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

(defn post-resource [resource body content-type]
  (let [ctx {:method   :post
             :request  {:headers
                              {"content-length" (str (count (bs/to-byte-array body)))
                               "content-type"   content-type}
                        :body (bs/to-input-stream body)}
             :resource resource}]

    (i/process-request-body ctx)))

(defn process-body
  ([content-type body-schema body]
   (process-body content-type body-schema nil body))
  ([content-type body-schema body-matcher body]
   (post-resource
     (resource
       {:methods
        {:post
         (merge {:consumes   content-type
                 :parameters {:body body-schema}
                 :response   ""}
                (when body-matcher
                  {:coercion-matchers {:body body-matcher}}))}})
     body
     content-type)))

(def process-json-body (partial process-body "application/json"))
(def process-edn-body (partial process-body "application/edn"))
(def process-plaintext-body (partial process-body "text/plain"))
(def process-transit-json-body (partial process-body "application/transit+json"))
(def process-transit-msgpack-body (partial process-body "application/transit+msgpack"))

;; TODO: test 400 errors
;; TODO: transit

(deftest json-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process-json-body {:foo s/Str}
                                      (json/encode {:foo "bar"}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo s/Keyword}
                                      (json/encode {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo Keyword}
                                      (json/encode {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo (s/eq :yoyo)}
                                      (json/encode {:foo :yoyo}))
                   [:parameters :body])))

    (let [processed (process-json-body {:foo (s/enum :yoyo :bar)}
                                       (json/encode {:foo :yoyo}))]
      (is (= {:foo :yoyo} (get-in processed [:parameters :body])))
      (is (= {:foo "yoyo"} (get-in processed [:body]))))

    (is (= 5
           (get-in (process-json-body s/Int
                                      (json/encode 5))
                   [:parameters :body])))

    (let [processed (process-json-body {:foo s/Int}
                                       #(when (= s/Int %) (constantly 1234))
                                       (json/encode {:foo 9999}))]
      (is (= {:foo 1234} (get-in processed [:parameters :body])))
      (is (= {:foo 9999} (get-in processed [:body])))))

  (testing "sad path"
    (is (thrown?
          ExceptionInfo
          (get-in
            (process-json-body
              {:foo s/Str}
              (json/encode {:foo 123}))
            [:parameters :body])))))

(deftest edn-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process-edn-body {:foo s/Str}
                                     (pr-str {:foo "bar"}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-edn-body {:foo s/Keyword}
                                     (pr-str {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-edn-body {:foo Keyword}
                                     (pr-str {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-edn-body {:foo (s/eq :yoyo)}
                                     (pr-str {:foo :yoyo}))
                   [:parameters :body])))

    (let [processed (process-edn-body {:foo (s/enum :yoyo :bar)}
                                      (pr-str {:foo :yoyo}))]
      (is (= {:foo :yoyo} (get-in processed [:parameters :body])))
      (is (= {:foo :yoyo} (get-in processed [:body]))))

    (is (= 5
           (get-in (process-edn-body s/Int
                                     (pr-str 5))
                   [:parameters :body])))

    (let [processed (process-edn-body {:foo s/Int}
                                      #(when (= s/Int %) (constantly 1234))
                                      (pr-str {:foo 9999}))]
      (is (= {:foo 1234} (get-in processed [:parameters :body])))
      (is (= {:foo 9999} (get-in processed [:body])))))

  (testing "sad path"
    (is (thrown?
          ExceptionInfo
          (get-in
            (process-edn-body
              {:foo s/Str}
              (pr-str {:foo 123}))
            [:parameters :body])))))

(deftest plaintext-test
  (testing "happy path"
    (is (= "foo"
           (get-in (process-plaintext-body s/Str "foo")
                   [:parameters :body])))
    (is (= :foo
           (get-in (process-plaintext-body s/Keyword "foo")
                   [:parameters :body])))
    (is (= :foo
           (get-in (process-plaintext-body Keyword "foo")
                   [:parameters :body])))
    (is (= :yoyo
           (get-in (process-plaintext-body (s/eq :yoyo) "yoyo")
                   [:parameters :body])))

    (let [processed (process-plaintext-body s/Int "5")]
      (is (= 5 (get-in processed [:parameters :body])))
      (is (= "5" (get-in processed [:body]))))

    (let [processed (process-plaintext-body s/Int
                                            #(when (= s/Int %) (constantly 1234))
                                            "9999")]
      (is (= 1234 (get-in processed [:parameters :body])))
      (is (= "9999" (get-in processed [:body])))))

  (testing "sad path"
    (is (thrown?
          ExceptionInfo
          (get-in
            (process-plaintext-body
              {:foo s/Str}
              "1234")
            [:parameters :body])))))

(defn- transit-encode [type v]
  (let [baos (ByteArrayOutputStream. 100)]
    (transit/write (transit/writer baos type) v)
    (.toByteArray baos)))

(def ^:private transit-json (partial transit-encode :json))
(def ^:private transit-msgpack (partial transit-encode :msgpack))

(deftest transit-json-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process-transit-json-body {:foo s/Str}
                                              (transit-json {:foo "bar"}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-json-body {:foo s/Keyword}
                                              (transit-json {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-json-body {:foo Keyword}
                                              (transit-json {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-json-body {:foo (s/eq :yoyo)}
                                              (transit-json {:foo :yoyo}))
                   [:parameters :body])))

    (let [processed (process-transit-json-body {:foo (s/enum :yoyo :bar)}
                                               (transit-json {:foo :yoyo}))]
      (is (= {:foo :yoyo} (get-in processed [:parameters :body])))
      (is (= {:foo :yoyo} (get-in processed [:body]))))

    (is (= 5
           (get-in (process-transit-json-body s/Int
                                              (transit-json 5))
                   [:parameters :body])))

    (let [processed (process-transit-json-body {:foo s/Int}
                                               #(when (= s/Int %) (constantly 1234))
                                               (transit-json {:foo 9999}))]
      (is (= {:foo 1234} (get-in processed [:parameters :body])))
      (is (= {:foo 9999} (get-in processed [:body])))))

  (testing "sad path"
    (is (thrown?
          ExceptionInfo
          (get-in
            (process-transit-json-body
              {:foo s/Str}
              (transit-json {:foo 123}))
            [:parameters :body])))))

(deftest transit-msgpack-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process-transit-msgpack-body {:foo s/Str}
                                                 (transit-msgpack {:foo "bar"}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-msgpack-body {:foo s/Keyword}
                                                 (transit-msgpack {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-msgpack-body {:foo Keyword}
                                                 (transit-msgpack {:foo :yoyo}))
                   [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-transit-msgpack-body {:foo (s/eq :yoyo)}
                                                 (transit-msgpack {:foo :yoyo}))
                   [:parameters :body])))

    (let [processed (process-transit-msgpack-body {:foo (s/enum :yoyo :bar)}
                                                  (transit-msgpack {:foo :yoyo}))]
      (is (= {:foo :yoyo} (get-in processed [:parameters :body])))
      (is (= {:foo :yoyo} (get-in processed [:body]))))

    (is (= 5
           (get-in (process-transit-msgpack-body s/Int
                                                 (transit-msgpack 5))
                   [:parameters :body])))

    (let [processed (process-transit-msgpack-body {:foo s/Int}
                                                  #(when (= s/Int %) (constantly 1234))
                                                  (transit-msgpack {:foo 9999}))]
      (is (= {:foo 1234} (get-in processed [:parameters :body])))
      (is (= {:foo 9999} (get-in processed [:body])))))

  (testing "sad path"
    (is (thrown?
          ExceptionInfo
          (get-in
            (process-transit-msgpack-body
              {:foo s/Str}
              (transit-msgpack {:foo 123}))
            [:parameters :body])))))
