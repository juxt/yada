;; Copyright © 2015, JUXT LTD.

(ns yada.etag-test
  (:require
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]
   [ring.mock.request :as mock]
   [yada.methods :refer [Get Post]]
   [yada.protocols :as p]
   [yada.test.util :refer (etag?)]
   [yada.yada :as yada :refer [yada]]))

;; ETags -------------------------------------------------------------

;; TODO Extract out into dedicated ns.

(defrecord ETagTestResource [v]
  p/ResourceProperties
  (properties [_]
    {:representations [{:media-type "text/plain"}]})
  (properties [_ ctx]
    {:version @v})
  Get
  (GET [_ ctx] "foo")
  Post
  (POST [_ {:keys [response]}] (assoc response :version (swap! v inc))))

(deftest etag-test
  (testing "etags-identical-for-consecutive-gets"
    (let [v (atom 1)
          handler (yada (->ETagTestResource v))
          r1 @(handler (mock/request :get "/"))
          r2 @(handler (mock/request :get "/"))]
      (given [r1 r2]
        [first :status] := 200
        [second :status] := 200
        [first :headers "etag"] :? etag?
        [second :headers "etag"] :? etag?
        )
      ;; ETags are the same in both responses
      (is (= (get-in r1 [:headers "etag"])
             (get-in r2 [:headers "etag"])))))

  (testing "etags-different-after-post"
    (let [v (atom 1)
          handler (yada (->ETagTestResource v))
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
          handler (yada (->ETagTestResource v))
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
