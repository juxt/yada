;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test the Hello World tutorial"}
    yada.helloworld-test
  (:require
   [clojure.test :refer :all]
   [cheshire.core :as json]
   [byte-streams :as bs]
   [ring.mock.request :refer [request]]
   [ring.util.time :refer (parse-date format-date)]
   [yada.dev.hello :as hello]
   [yada.media-type :as mt]
   [yada.util :refer (parse-csv)]
   [yada.test-util :refer (etag? to-string)]
   [yada.yada :as yada])
  (:import [java.util Date]))

(defn validate-headers? [headers]
  (->>
   (for [[k v] headers]
     (case k
       "content-length" nil
       "content-type" (when-not (mt/string->media-type v) "mime-type not valid")
       "last-modified" (when-not (instance? Date (parse-date v)) "last-modified not a date")
       "vary" (when-not (pos? (count (parse-csv v))) "vary empty")
       "allow" (when-not (pos? (count (parse-csv v))) "allow empty")
       "etag" (when-not (etag? v) "not a valid etag")
       "strict-transport-security" nil
       "content-security-policy" nil
       "x-frame-options" nil
       "x-xss-protection" nil
       "x-content-type-options" nil
       (throw (ex-info "Cannot validate unrecognized header" {:k k :v v}))))
   (remove nil?) vec))

(deftest string-test
  (let [resource (hello/hello)
        response @(resource (request :get "/"))
        headers (:headers response)]
    (is (= 200 (:status response)))
    (is (= #{"last-modified" "content-type" "content-length" "vary" "etag" "x-frame-options" "x-xss-protection" "x-content-type-options"} (set (keys headers))))
    (is (= [] (validate-headers? headers)))
    (is (= "text/plain;charset=utf-8" (get headers "content-type")))
    ;; TODO: See github issue regarding ints and strings
    (is (= 13 (get headers "content-length")))
    (is (= #{"accept-charset"} (set (parse-csv (get headers "vary")))))
    (is (= "-648266692" (get headers "etag")))
    (is (= "Hello World!\n" (to-string (:body response))))))

(deftest swagger-intro-test
  (let [resource (hello/hello-swagger)
        response @(resource (request :get "/swagger.json"))]
    #_(given response
      :status := 200
      [:headers keys set] := #{"last-modified" "content-type" "content-length" "vary" "etag"}
      [:headers "content-type"] := "application/json"
      [:headers "content-length"] := 409
      [:headers "vary" parse-csv set] := #{"accept-charset"}
      [:headers "etag"] := "-570723708"
      )
    #_(given (-> response :body to-string json/decode)
      ["swagger"] := "2.0"
      ["info" "title"] := "Hello World!"
      ["info" "version"] := "1.0"
      ["info" "description"] := "Demonstrating yada + swagger"
      ["paths" "/hello" "get" "produces"] := ["text/plain"]
      )))

(deftest conditional-request
  (testing "Dates"
    (let [resource (hello/hello)
          response @(resource (request :get "/"))
          last-modified (some-> response (get-in [:headers "last-modified"]) parse-date)
          etag (some-> response (get-in [:headers "etag"]))]

      (is last-modified)
      (is (instance? Date last-modified))

      (let [response @(resource
                       (merge-with merge (request :get "/" )
                                   {:headers {"if-modified-since"
                                              (format-date (-> last-modified .toInstant (.plusSeconds 1) Date/from))}}))]
        (is (= 304 (:status response))))

      (let [response @(resource
                         (merge-with merge (request :get "/" )
                                     {:headers {"if-modified-since"
                                                (format-date (-> last-modified .toInstant (.minusSeconds 1) Date/from))}}))]
        (is (= 200 (:status response))))

      (is etag)

      (let [response @(resource
                         (merge-with merge (request :get "/" )
                                     {:headers {"if-none-match" etag}}))]
        (is (= 304 (:status response)))))))

(deftest put-not-allowed-test
  (let [resource (hello/hello)
        response @(resource (request :put "/"))
        headers (:headers response)]
    (is (= 405 (:status response)))
    (is (contains? (set (keys headers)) "allow"))
    (is (= [] (validate-headers? headers)))
    (is (= #{"OPTIONS" "GET" "HEAD"} (set (parse-csv (get headers "allow")))))))

(deftest options-test
  (let [resource (hello/hello)
        response @(resource (request :options "/"))
        headers (:headers response)]
    (is (= 200 (:status response)))
    (is (=  #{"allow" "content-length" "x-frame-options" "x-xss-protection" "x-content-type-options"} (set (keys headers))))
    (is (= [] (validate-headers? headers)))
    (is (= #{"OPTIONS" "GET" "HEAD"} (set (parse-csv (get headers "allow")))))
    (is (nil? (:body response)))))

(deftest atom-options-test
  (let [resource (hello/hello-atom)
        response @(resource (request :options "/"))
        headers (:headers response)]
    (is (= 200 (:status response)))
    (is (= #{"allow" "content-length" "x-frame-options" "x-xss-protection" "x-content-type-options"} (set (keys headers))))
    (is (= [] (validate-headers? headers)))
    (is (= #{"OPTIONS" "GET" "HEAD" "PUT" "DELETE"} (set (parse-csv (get headers "allow")))))
    (is (nil? (:body response)))))

#_(deftest put-test
  (let [resource (hello/hello-atom)]
    (given @(resource (merge (request :put "/")
                             {:body "Hello Dolly!"}))
      ;; What should a PUT return in this case?
      :status := 204
      [:headers keys set] := #{}
      [:headers validate-headers?] := []
      :body := nil)))

;; TODO: head request

#_(deftest parameters-test
  (let [resource hello/hello-parameters]
    (given @(resource (request :get "/?p=Ken"))
      :status := 200
      ;; TODO: Test a lot more, like content-type
      [:body to-string] := "Hello Ken!\n")))

;; TODO: content negotiation

#_(deftest content-negotiation-test
  (let [resource tutorial/hello-languages]
    (testing "default is Simplified Chinese"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")))
        :status := 200
        [:headers "content-language"] := "zh-ch"
        ;; TODO: Test a lot more, like content-type
        [:body to-string] := "你好世界!\n"))

    (testing "English is available on request"
      (given @(resource (-> (request :get "/")
                            (assoc-in [:headers "accept"] "text/plain")
                            (assoc-in [:headers "accept-language"] "en")))
        :status := 200
        ;; TODO: Test a lot more, like content-type
        [:headers "content-language"] := "en"
        [:body to-string] := "Hello World!\n"))))

;; TODO: Fix this horrible issue when resources fail to compile

;; java.lang.IllegalArgumentException: No implementation of method: :resolve-handler of protocol: #'bidi.bidi/Matched found for class: java.lang.Boolean
;; 	at clojure.core$_cache_protocol_fn.invoke(core_deftype.clj:554)
;; 	at bidi.bidi$eval10740$fn__10754$G__10729__10761.invoke(bidi.cljc:183)
;; 	at bidi.bidi$match_pair.invoke(bidi.cljc:193)
;; 	at bidi.bidi$eval10852$fn__10853$fn__10854.invoke(bidi.cljc:307)
;; 	at clojure.core$some.invoke(core.clj:2570)
;; 	at bidi.bidi$eval10852$fn__10853.invoke(bidi.cljc:307)
;; 	at bidi.bidi$eval10740$fn__10754$G__10729__10761.invoke(bidi.cljc:183)
;; 	at bidi.bidi$match_pair.invoke(bidi.cljc:193)
;; 	at bidi.bidi$match_route_STAR_.invoke(bidi.cljc:353)
;; 	at bidi.ring$make_handler$fn__12583.invoke(ring.clj:36)
;; 	at clojure.core$some_fn$sp2__6927.invoke(core.clj:7148)
;; 	at aleph.http.server$handle_request$fn__35246$f__20364__auto____35247.invoke(server.clj:153)
;; 	at clojure.lang.AFn.run(AFn.java:22)
;; 	at io.aleph.dirigiste.Executor$Worker$1.run(Executor.java:62)
;; 	at manifold.executor$thread_factory$reify__20246$f__20247.invoke(executor.clj:36)
;; 	at clojure.lang.AFn.run(AFn.java:22)
;; 	at java.lang.Thread.run(Thread.java:745)

