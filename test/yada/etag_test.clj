;; Copyright © 2015, JUXT LTD.

(ns yada.etag-test
  (:require
   [clojure.test :refer :all]
   [clojure.tools.logging :refer :all]
   [juxt.iota :refer [given]]
   [ring.mock.request :as mock]
   [yada.resource :refer [resource]]
   [yada.protocols :as p]
   [yada.test.util :refer (etag?)]
   [yada.yada :as yada :refer [yada]]))

;; ETags -------------------------------------------------------------

(defn etag-test-resource [v]
  (resource
   {:properties (fn [ctx] {:version @v})
    ;; TODO: test with just map, or even just "text/plain"
    :produces [{:media-type "text/plain"}]
    :methods {:get {:handler (fn [ctx] "foo")}
              :post {:handler (fn [{:keys [response]}]
                                (assoc response :version (swap! v inc)))}}}))

(deftest etag-test
  (testing "etags-identical-for-consecutive-gets"
    (let [v (atom 1)
          handler (yada (etag-test-resource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :get "/"))]
      (given [r1 r2]
        [first :status] := 200
        [second :status] := 200
        [first :headers "etag"] :? etag?
        [second :headers "etag"] :? etag?)
      ;; ETags are the same in both responses
      (is (= (get-in r1 [:headers "etag"])
             (get-in r2 [:headers "etag"])))))

  (testing "etags-different-after-post"
    (let [v (atom 1)
          handler (yada (etag-test-resource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :post "/"))
          r3 @(handler (mock/request :get "/"))]
      (given [r1 r3]
        [first :status] := 200
        [second :status] := 200
        [first :headers "etag"] :? etag?
        [second :headers "etag"] :? etag?)
      (given r2
        :status := 200)
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

        (given @(handler (-> (mock/request :post "/")
                             (update-in [:headers] merge {"if-match" etag})))
          :status := 412)

        (given @(handler (-> (mock/request :post "/")
                             (update-in [:headers] merge {"if-match" (str "abc, " etag ",123")})))
          :status := 412))

      ;; Happy path - POSTing from a fresh etag (from r2)
      (let [etag (get-in r2 [:headers "etag"])]
        (is (etag? etag))
        (given @(handler (-> (mock/request :post "/")
                             (update-in [:headers] merge {"if-match" (str "abc, " etag ",123")})))
          :status := 200)))))
