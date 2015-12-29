;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "These are yada's own sanity checks for the phonebook
    example, which has its own tests too."}
    yada.phonebook-test
    (:require
     [aleph.http :as http]
     [aleph.http.client-middleware :as middleware]
     [bidi.bidi :refer [match-route routes] :as bidi]
     [bidi.ring :refer [make-handler]]
     [bidi.schema :refer [RoutePair]]
     [byte-streams :as b]
     [clojure.set :as set]
     [clojure.test :refer :all]
     [clojure.tools.logging :refer :all]
     [com.stuartsierra.component :refer [system-using system-map]]
     [manifold.deferred :as d]
     [manifold.stream :as ms]
     [modular.aleph :refer [new-webserver]]
     [modular.test :refer [with-system-fixture *system*]]
     [phonebook.system :refer [new-phonebook]]
     [ring.util.codec :as codec]
     [schema.core :as s]
     schema.test
     [yada.util :refer [CRLF]]))

(defn new-system
  "Define a minimal system which is just enough for the tests in this
  namespace to run"
  []
  (-> (system-map
       :phonebook (new-phonebook {:port 9015
                                  :entries {1 {:surname "Sparks"
                                               :firstname "Malcolm"
                                               :phone "1234"}
                                            2 {:surname "Pither"
                                               :firstname "Jon"
                                               :phone "1235"}}}))
      (system-using {})))

(use-fixtures :each (with-system-fixture new-system))
(use-fixtures :once schema.test/validate-schemas)

(deftest system-sanity-test
  (let [s (get-in *system* [:phonebook :api :routes])]
    (is (not (nil? s)))
    ;; TODO: Not quite sure why this fails
    ;;(is (nil? (s/check RoutePair s)))
    ))

(def request-decorators
  [middleware/decorate-method
   middleware/decorate-basic-auth
   middleware/decorate-url])

(defn wrap-request
  "Returns a batteries-included HTTP request function corresponding to the given
  core client. See default-middleware for the middleware wrappers that are used
  by default"
  [client]
  (let [client' client]
    (fn [req]
      (if (:aleph.http.client/close req)
        (client req)
        (let [req' (reduce #(%2 %1) req request-decorators)]
          (d/chain' (client' req')
                    ;; coerce the response body
                    (fn [{:keys [body] :as rsp}]
                      (if body
                        (middleware/coerce-response-body req' rsp)
                        rsp))))))))

(def test-connection-pool
  (http/connection-pool
   {:middleware wrap-request}))

(deftest post-test
  (let [prefix "http://localhost:9015"
        post-response
        @(http/post
          (str prefix "/phonebook")
          {:pool test-connection-pool
           :basic-auth ["tom" "watson"]
           :follow-redirects false
           :headers {"content-type" "application/x-www-form-urlencoded"
                     "origin" "http://localhost:9015"
                     ;; Must set the host to be the same origin in
                     ;; order to get redirects to happen. Redirects
                     ;; are "disallowed for cross-origin requests that
                     ;; require preflight."
                     "host" "localhost:9015"}
           :body (codec/form-encode {:firstname "Kath" :surname "Read" :phone "1236"})})]
    (is (= 303 (:status post-response)))
    (let [location (get-in post-response [:headers "location"])]
      (is (re-matches #"/phonebook/\d+" location)))
    (let [headers (set (keys (get post-response :headers)))]
      (is (set/superset? headers #{"content-length"}))
      (is (not (set/superset? headers #{"content-type"}))))
    
    (testing "Get what we've posted"
      (let [location (get-in post-response [:headers "location"])]
        (is (re-matches #"/phonebook/\d+" location))
        (let [get-response @(http/get
                             (str prefix location)
                             {:pool test-connection-pool
                              :basic-auth ["tom" "watson"]
                              :follow-redirects false})]
          (is (= 200 (:status get-response))))))))

(def boundary "BoundaryZ3oJB7WHOBmOjrEi")

(defn encode-multipart-formdata [m]
  (apply str
         (concat
          (for [[k v] m]
            (str (str "--" boundary CRLF)
                 (format "Content-Disposition: form-data; name=\"%s\"" (name k))
                 CRLF CRLF v CRLF))
          [(str "--" boundary "--")])))

(defn encode-unterminated-multipart-formdata
  "Create some bad multipart data that hasn't been properly
  terminated."
  [m]
  (->>
   (for [[k v] m]
     (str (format "Content-Disposition: form-data; name=\"%s\"" (name k))
          CRLF CRLF v CRLF))
   (interpose (str "--" boundary CRLF))
   (apply str)))

(deftest bad-put-test
  (let [formdata (encode-unterminated-multipart-formdata {:firstname "Jon" :surname "Pither" :phone "321"})
        content-length (count (.getBytes formdata "UTF-8"))
        response
        @(http/put
          "http://localhost:9015/phonebook/2"
          {:pool test-connection-pool
           :follow-redirects false
           :basic-auth ["tom" "watson"]
           :headers
           {"content-type" (format "multipart/form-data;charset=utf-8;boundary=%s" boundary)
            "content-length" content-length}
           :body formdata})]
    (is (= 400 (:status response)))
    (is (re-seq #"Multipart not properly terminated" (-> response :body b/to-string)))))

(deftest put-test
  (let [formdata (encode-multipart-formdata {:firstname "Jon" :surname "Pither" :phone "321"})
        content-length (count (.getBytes formdata "UTF-8"))
        response
        @(http/put
          "http://localhost:9015/phonebook/2"
          {:pool test-connection-pool
           :basic-auth ["tom" "watson"]
           :follow-redirects false
           :headers {"content-type" (format "multipart/form-data;charset=utf-8;boundary=%s" boundary)
                     "content-length" content-length}
           ;; We can switch on transfer encoding by transforming to byte-buffers
           ;; :body (b/to-byte-buffers formdata)
           :body formdata})]
    (is (= 204 (:status response)))

    (is (not (nil? (:body response))))
    (is (= "" (b/to-string (:body response))))

    ;; TODO: Fix this. Possibly the update didn't work - see what body
    ;; was parsed, etc. How far did the put get? Ensure we can't put
    ;; nil to the database

    (let [phonebook @(get-in *system* [:phonebook :atom-db :phonebook])]
      (is (not (nil? (get phonebook 2)))))

    (testing "Get what we've put"
      (let [get-response @(http/get
                           "http://localhost:9015/phonebook/2"
                           {:pool test-connection-pool
                            :basic-auth ["tom" "watson"]
                            :follow-redirects false})]
        (is (= (:status get-response) 200))))))

(deftest delete-test
  (let [response
        @(http/delete
          "http://localhost:9015/phonebook/2"
          {:pool test-connection-pool
           :basic-auth ["tom" "watson"]
           :follow-redirects false})]
    
    (is (= 204 (:status response)))

    (let [phonebook @(get-in *system* [:phonebook :atom-db :phonebook])]
      (is (not (nil? (get phonebook 1))))
      ;; Check we deleted it
      (is (nil? (get phonebook 2))))

    (testing "Get what we've deleted"
      (let [get-response @(http/get
                           "http://localhost:9015/phonebook/2"
                           {:pool test-connection-pool
                            ;; TODO: Why do we get edn when no accept header? Because no accept header implies */*, and error negotiation means that edn wins.
                            ;;:headers {"accept" "text/html"}
                            :basic-auth ["tom" "watson"]
                            :follow-redirects false})]

        (is (= 404 (:status get-response)))))))

;; TODO: Would be great to be able to look up a handler by tag - file somewhere
;; Use route-seq
#_(-> dev/system :phonebook :server :routes (bidi/match-route "/phonebook") :handler
    :methods :post :consumes)
