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
  (let [op (match-route spec (:uri req))
        handler (make-handler-from-swagger-resource (:bidi.swagger/resource op) opts)]
    (let [res (handler req)]
      (if (d/deferrable? res) @res res))))

(deftest handlers
  (testing "Service Unavailable"
    (let [response (get-op-response spec (mock/request :get "/pets")
                                    :service-available? false)]
      (is (= (-> response :status) 503)))
    (let [response (get-op-response spec (mock/request :get "/pets")
                                    :service-available? true)]
      (is (not= (-> response :status) 503))))

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

  (testing "Method Not Allowed"
    (let [response (get-op-response spec (mock/request :put "/pets"))]
      (is (= (-> response :status) 405))))

  (testing "OK"
    (let [response (get-op-response spec (mock/request :get "/pets"))]
      (is (= (-> response :status) 200))
      (is (= (-> response :body) "Hello World!"))))

  (testing "Not found"
    (let [response (get-op-response spec (mock/request :get "/pets") :resource-metadata (constantly nil))]
      (is (= (-> response :status) 404)))))

;; TODO: Example
;; TODO: Response body coercion
;; TODO: Auth
;; TODO: Conneg
;; TODO: CORS/OPTIONS
;; TODO: CSRF
;; TODO: Cache-headers
