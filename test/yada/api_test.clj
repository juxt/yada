;; Copyright © 2015, JUXT LTD.

(ns yada.api-test
  (:require
   [com.stuartsierra.component :refer (system-using system-map)]
   [bidi.bidi :refer (match-route routes)]
   [bidi.ring :refer (make-handler)]
   [clojure.test :refer :all]
   [yada.yada :refer (yada) :as yada]
   [yada.test.util :refer (given)]
   [manifold.deferred :as d]
   [modular.test :refer (with-system-fixture *system*)]
   [ring.mock.request :as mock]
   [yada.dev.user-api :refer (new-user-api)]
   [yada.dev.database :refer (new-database)]
   ))

(defn new-system
  "Define a minimal system which is just enough for the tests in this
  namespace to run"
  []
  (system-using
   (system-map
    :api (new-user-api))
    {}))

(use-fixtures :each (with-system-fixture new-system))

(defn get-api []
  (-> *system* :api))

#_(defn get-op-response [api req & {:as opts}]
  (let [h (yada opts)
        {rp :route-params} (match-route api (:uri req))]
    (let [res (h (assoc req :route-params rp))]
      (if (d/deferrable? res) @res res))))

;; First get this API integration test working, then build a proper swagger_test suite

(deftest api-test
  (let [h (make-handler (routes (:api *system*)))
        req (mock/request :get "/api/swagger.json")]
    (given @(h req)
      :status := 200
      :headers :> {"content-type" "application/json"})))


#_(deftest handlers
  (testing "Service Unavailable"

    (let [response (get-op-response
                    (get-api) (mock/request :get "/pets") :service-available? false)]
      (is (= (-> response :status) 503)))

    (let [response (get-op-response
                    (get-api) (mock/request :get "/pets") :service-available? true)]
      (is (not= (-> response :status) 503)))

    (testing "Deferred"
      (let [response
            (get-op-response
             (get-api) (mock/request :get "/pets")
             ;; Check status of slow backend system without blocking this thread.
             :service-available? #(future (Thread/sleep 1) false))]
        (is (= (-> response :status) 503)))))

  (testing "Not Implemented"
    (let [response (get-op-response (get-api) (mock/request :play "/pets"))]
      (is (= (-> response :status) 501)))
    (let [response (get-op-response (get-api) (mock/request :play "/pets")
                                    :known-method? (fn [x] (= x :play)))]
      (is (not= (-> response :status) 501))))

  (testing "Request URI Too Long"
    (let [response (get-op-response (get-api) (mock/request :get "/pets")
                                    :request-uri-too-long? 4)]
      (is (= (-> response :status) 414))))

  ;; TODO Reinstate - can't do this without knowledge of other operations
  #_(testing "Method Not Allowed"
      (let [response (get-op-response (get-api) (mock/request :put "/pets"))]
        (is (= (-> response :status) 405))))

  (testing "OK"
    (let [response (get-op-response (get-api) (-> (mock/request :get "/pets")
                                                (mock/header "Accept" "text/html")))]
      (is (= (-> response :status) 200))
      ;; Some default exists
      (is (not (nil? (-> response :body))))
      (is (string? (-> response :body)))))

  (testing "Not found"
    (let [response (get-op-response
                    (get-api)
                    (mock/request :get "/pets/100")
                    :find-resource false)]
      (is (= (-> response :status) 404)))))


;; TODO: Response body coercion
;; TODO: Auth
;; TODO: Conneg
;; TODO: CORS/OPTIONS
;; TODO: CSRF
;; TODO: Cache-headers
;; TODO: Vary
