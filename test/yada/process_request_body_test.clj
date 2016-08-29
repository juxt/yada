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
    [clojure.string :as str]
    [yada.multipart :as mp]
    [yada.coerce :as coerce]
    [ring.swagger.coerce :as rsc])
  (:import (clojure.lang ExceptionInfo Keyword)
           (yada.multipart DefaultPart)))

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

(defn process-json-body [body-schema body]
  (post-resource
    (resource
      {:methods
       {:post
        {:consumes   "application/json"
         :parameters {:body body-schema}
         :response   ""}}})
    body
    "application/json"))

;; TODO: test 400 errors
;; TODO: transit

(declare thrown?)

(deftest post-parameter-coercion-test
  (testing "happy path"
    (is (= {:foo "bar"}
           (get-in (process-json-body {:foo s/Str}
                                      (json/encode {:foo "bar"})
                                      ) [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo s/Keyword}
                                      (json/encode {:foo :yoyo})
                                      ) [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo Keyword}
                                      (json/encode {:foo :yoyo})
                                      ) [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo (s/eq :yoyo)}
                                      (json/encode {:foo :yoyo})
                                      ) [:parameters :body])))
    (is (= {:foo :yoyo}
           (get-in (process-json-body {:foo (s/enum :yoyo :bar)}
                                      (json/encode {:foo :yoyo})
                                      ) [:parameters :body]))))
  (testing "sad path"
    (is (thrown? ExceptionInfo
                 (get-in (process-json-body {:foo s/Str}
                                            (json/encode {:foo 123})
                                            ) [:parameters :body])))))


(defn process-multipart-body [post-spec body]
  (-> @(post-resource
         (resource {:methods
                    {:post
                     (merge
                       {:consumes "multipart/form-data"
                        :response ""}
                       post-spec)}})
         body
         "multipart/form-data; boundary=Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5")
      :parameters
      :body))

(defn crlf [s]
  (str/replace s #"(\r\n|\n|\r)" "\r\n"))

(def simple-key-value-pairs-body
  (crlf
    "--Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5
Content-Disposition: form-data; name=\"foo\"
Content-Type: text/plain; charset=UTF-8

bar
--Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5
Content-Disposition: form-data; name=\"baz\"
Content-Type: text/plain; charset=UTF-8

qux
--Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5--"))

(def int-as-value-body
  (crlf
    "--Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5
Content-Disposition: form-data; name=\"foo\"
Content-Type: text/plain; charset=UTF-8

5
--Ep2kpEHnLFQ_Zk6_KlJVexP-nMb5kF-5--"))

(defrecord IntAndStrPartConsumer []
  mp/PartConsumer
  (consume-part [_ state part]
    (update state :parts (fnil conj []) (mp/map->DefaultPart part)))
  (start-partial [_ piece]
    (mp/->DefaultPartial piece))
  (part-coercion-matcher [_]
    ;; Coerce a DefaultPart into the following keys
    (fn [schema]
      (get
        {s/Str (fn [^DefaultPart part]
                 (let [offset (get part :body-offset 0)]
                   (String. (:bytes part) offset (- (count (:bytes part)) offset))))
         s/Int (fn [^DefaultPart part]
                 (let [offset (get part :body-offset 0)]
                   45))}
        schema))

    ;; this is a lookup table of schema->matcher
    ;; matcher takes a defaultpart and returns something that should match the schema
    ))

(deftest multipart-test
  (testing "simple key-value pairs"
    (is (= {:foo "bar"
            :baz "qux"}
           (process-multipart-body
             {:parameters
              {:body
               {:foo s/Str
                :baz s/Str}}}
             simple-key-value-pairs-body))))

  (testing "int as value using built-in coercion"
    (try
      (let [result (process-multipart-body
                     {:parameters {:body {:foo s/Int}}}
                     int-as-value-body)]
        (is (= {:foo 5} result)))
      (catch Exception e
        (is (nil? e)))))

  (testing "int as value using built-in coercion plus custom part consumer"
    (try
      (let [result (process-multipart-body
                     {:parameters    {:body {:foo s/Int}}
                      :part-consumer (->IntAndStrPartConsumer)}
                     int-as-value-body)]
        (is (= {:foo 5} result)))
      (catch Exception e
        (is (nil? e)))))

  (testing "int as value using hand-wired coercion"
    (try
      (let [part-consumer (->IntAndStrPartConsumer)
            result (process-multipart-body
                     {:parameters        {:body {:foo s/Int}}
                      :part-consumer     part-consumer
                      :coercion-matchers {:body
                                          (fn [schema]
                                            ;; note comments below, I think this is a bug
                                            (or
                                              (comment not needed as we are inside matcher now
                                                       (when matcher (matcher schema)))

                                              (comment moved this up one)
                                              ((mp/part-coercion-matcher part-consumer) schema)

                                              (comment moved this down one)
                                              (coerce/+parameter-key-coercions+ schema)

                                              ((rsc/coercer :json) schema)))}}
                     int-as-value-body)]
        (is (= {:foo 45} result)))
      (catch Exception e
        (is (nil? e))))))
