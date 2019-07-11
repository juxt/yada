(ns yada.cookies-test
  (:require
   [yada.cookies :as cookies]
   [yada.test-util :as utils]
   [clojure.test :refer :all]
   [yada.yada :as yada])
  (:import (java.util Date)))

(deftest cookie-test
  (let [cookies
        {:session {:name "session"
                   :consumer (fn [ctx cookie v] (assoc ctx :cookie-val v))}
         :tracker {:name "tracker"}}]

    (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (yada/set-cookie :session 123)
                    (yada/set-cookie :tracker "xyz")))}}})]

      (is
       (= {:status 200,
           :headers
           {"set-cookie" ["session=123" "tracker=xyz"]},
           :body nil} (update response :headers select-keys ["set-cookie"]))))

    (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (or (:cookie-val ctx) "none"))}}}
           :get "/" {:headers {"cookie" "session=123"}})]

      (is (= "123" (:body response))))

    (testing "unset cookie"
      (let [response
          (yada/response-for
           {:cookies cookies
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn [ctx]
                (-> ctx
                    (yada/unset-cookie :session)))}}})]
        (is
         (= {:status 200,
             :headers
             {"set-cookie" ["session=; Expires=Thu, 01 Jan 1970 00:00:00 +0000"]},
             :body nil} (update response :headers select-keys ["set-cookie"])))))

    (testing "exercising all cookie attributes"
      (let [response
            (yada/response-for
             {:cookies {:session {:name "session"
                                  :max-age 3600
                                  :expires (fn [_] (Date. 0))
                                  :domain "example.com"
                                  :path "/"
                                  :secure true
                                  :same-site :strict
                                  :http-only true}}
              :methods
              {:get
               {:produces "text/plain"
                :response
                (fn [ctx]
                  (-> ctx
                      (yada/set-cookie :session "xyz")))}}})]
        (is
         (= {:status 200
             :headers {"set-cookie" ["session=xyz; Domain=example.com; Expires=Thu, 01 Jan 1970 00:00:00 +0000; HttpOnly; Max-Age=3600; Path=/; SameSite=Strict; Secure"]}
             :body nil}
            (update response :headers select-keys ["set-cookie"])))))))

(deftest unset-cookie-test
  (testing "Unsetting a cookie in a consumer when authorization has failed"
    (let [cookies {:session {:name "session"
                             :consumer (fn [ctx cookie v]
                                         (yada/unset-cookie ctx "session"))}}]
      (let [response
            (yada/response-for
             {:cookies cookies
              :authorization {:authorize (fn [ctx creds self]
                                           false)}
              :methods
              {:get
               {:produces "text/plain"
                :response
                (fn [ctx]
                  (throw (ex-info "We should never get here" {})))}}}
             :get
             "/"
             {:headers {"cookie" "session=abc"}})]
        (is (utils/submap?
             {:status 401
              :headers {"set-cookie" ["session=; Expires=Thu, 01 Jan 1970 00:00:00 +0000"]}}
             response)))))
  (testing "Unsetting a cookie in a thrown response"
    (let [response
          (yada/response-for
           {:cookies
            {:session {:name "session"
                       :consumer (fn [ctx cookie v]
                                   (yada/unset-cookie ctx "session"))}}
            :methods
            {:get
             {:produces "text/plain"
              :response
              (fn[ctx]
                (throw
                 (ex-info "Setting a cookie in response"
                          ;; Note that the cookies aren't preserved if the thrown
                          ;; ex-data doesn't include a :status.
                          {:status 500
                           :cookies (:cookies (:response (yada/unset-cookie ctx "session")))
                           :yada.handler/disable-error-logging? true})))}}})]
      (is (utils/submap?
           {:status 500
            :headers {"set-cookie" ["session=; Expires=Thu, 01 Jan 1970 00:00:00 +0000"]}}
           response)))))
