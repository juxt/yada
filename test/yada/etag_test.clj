;; Copyright © 2015, JUXT LTD.

(ns yada.etag-test
  (:require
   [clojure.test :refer :all]
   [clojure.tools.logging :refer :all]
   [ring.mock.request :as mock]
   [yada.resource :refer [resource]]
   [yada.test-util :refer [etag?]]
   [yada.yada :as yada :refer [yada]]))

;; ETags -------------------------------------------------------------

(defn etag-test-resource [v]
  (resource
   {:properties (fn [ctx] {:version @v})
    ;; TODO: test with just map, or even just "text/plain"
    :produces [{:media-type "text/plain"}]
    :methods {:get {:response (fn [ctx] "foo")}
              :post {:response (fn [{:keys [response]}]
                                (assoc response :version (swap! v inc)))}}}))

(deftest etag-test
  (testing "etags-identical-for-consecutive-gets"
    (let [v (atom 1)
          handler (yada (etag-test-resource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :get "/"))]

      (is (= 200 (:status r1)))
      (is (etag? (get-in r1 [:headers "etag"])))
      (is (= 200 (:status r2)))
      (is (etag? (get-in r2 [:headers "etag"])))

      ;; ETags are the same in both responses
      (is (= (get-in r1 [:headers "etag"])
             (get-in r2 [:headers "etag"])))))

  (testing "etags-different-after-post"
    (let [v (atom 1)
          handler (yada (etag-test-resource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :post "/"))
          r3 @(handler (mock/request :get "/"))]

      (is (= 200 (:status r1)))
      (is (etag? (get-in r1 [:headers "etag"])))
      (is (= 200 (:status r3)))
      (is (etag? (get-in r3 [:headers "etag"])))

      (is (= 200 (:status r2)))

      ;; ETags are the same in both responses
      (is (not (= (get-in r1 [:headers "etag"])
                  (get-in r3 [:headers "etag"]))))))

  (testing "post-using-etags"
    (let [v (atom 1)
          handler (yada (etag-test-resource v))
          r1 @(handler (mock/request :get "/"))
          ;; Someone else POSTs, causing the etag given in r1 to become stale
          r2 @(handler (mock/request :post "/"))]

      ;; Sad path - POSTing with a stale etag (from r1)
      (let [etag (get-in r1 [:headers "etag"])]
        (let [r @(handler (-> (mock/request :post "/")
                              (update-in [:headers] merge {"if-match" etag})))]
          (is (= 412 (:status r))))

        (let [r @(handler (-> (mock/request :post "/")
                              (update-in [:headers] merge {"if-match" (str "abc, " etag ",123")})))]
          (is (= 412 (:status r)))))

      ;; Happy path - POSTing from a fresh etag (from r2)
      (let [etag (get-in r2 [:headers "etag"])]
        (is (etag? etag))
        (let [r @(handler (-> (mock/request :post "/")
                              (update-in [:headers] merge {"if-match" (str "abc, " etag ",123")})))]
          (is (= 200 (:status r))))))))
