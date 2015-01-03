;; Copyright © 2015, JUXT LTD.

(ns yada.core-test
  (:require
   [bidi.bidi :refer (match-route)]
   [clojure.core.match :refer (match)]
   [clojure.test :refer :all]
   [yada.core :refer :all]
   [manifold.deferred :as d]
   [ring.mock.request :as mock]
   [pets :refer (pets-spec) :rename {pets-spec spec}]))

(defn get-op-response [spec req & {:as opts}]
  (let [matched (match-route spec (:uri req))
        handler (make-handler (:handler matched) opts)]
    (let [res (handler (assoc req :route-params (:route-params matched)))]
      (if (d/deferrable? res) @res res))))

(deftest handlers
  (testing "Service Unavailable"

    (let [response (get-op-response
                    spec (mock/request :get "/pets") :service-available? false)]
      (is (= (-> response :status) 503)))

    (let [response (get-op-response
                    spec (mock/request :get "/pets") :service-available? true)]
      (is (not= (-> response :status) 503)))

    (testing "Deferred"
      (let [response
            (get-op-response
             spec (mock/request :get "/pets")
             ;; Check status of slow backend system without blocking this thread.
             :service-available? #(future (Thread/sleep 1) false))]
        (is (= (-> response :status) 503)))))

  (testing "Not Implemented"
    (let [response (get-op-response spec (mock/request :play "/pets"))]
      (is (= (-> response :status) 501)))
    (let [response (get-op-response spec (mock/request :play "/pets")
                                    :known-method? (fn [x] (= x :play)))]
      (is (not= (-> response :status) 501))))

  (testing "Request URI Too Long"
    (let [response (get-op-response spec (mock/request :get "/pets")
                                    :request-uri-too-long? 4)]
      (is (= (-> response :status) 414))))

  ;; TODO Reinstate - can't do this without knowledge of other operations
  #_(testing "Method Not Allowed"
    (let [response (get-op-response spec (mock/request :put "/pets"))]
      (is (= (-> response :status) 405))))

  (testing "OK"
    (let [response (get-op-response spec (-> (mock/request :get "/pets")
                                           (mock/header "Accept" "text/html")))]
      (is (= (-> response :status) 200))
      ;; Some default exists
      (is (not (nil? (-> response :body))))
      (is (string? (-> response :body)))))

  (testing "Not found"
    (let [response (get-op-response
                    spec
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
