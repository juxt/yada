;; Copyright © 2015, JUXT LTD.

(ns yada.phonebook-test
  (:require
   [aleph.http :as http]
   [aleph.http.client-middleware :as middleware]
   [bidi.bidi :refer [match-route routes] :as bidi]
   [bidi.ring :refer [make-handler]]
   [bidi.schema :refer [RoutePair]]
   [byte-streams :as b]
   [juxt.iota :refer [given]]
   [clojure.test :refer :all]
   [com.stuartsierra.component :refer [system-using system-map]]
   [manifold.deferred :as d]
   [manifold.stream :as ms]
   [modular.aleph :refer [new-webserver]]
   [modular.test :refer [with-system-fixture *system*]]
   [phonebook.system :refer [new-phonebook]]
   [ring.util.codec :as codec]
   [schema.core :as s]
   schema.test))

(def boundary "----BoundaryZ3oJB7WHOBmOjrEi")

(defn create-body
  ([] (create-body 20))
  ([n]
   (b/to-byte-buffers
    (str
     (char 13) (char 10) "--" boundary
     "Content-Disposition: form-data; name=\"firstname\"" (char 13) (char 10)
     (char 13) (char 10)
     (apply str (repeat n (apply str (map char (range (int \A) (inc (int \Z)))))))))))

(defn new-system
  "Define a minimal system which is just enough for the tests in this
  namespace to run"
  []
  (system-using
   (system-map
    :phonebook (new-phonebook {:port 9015
                               :entries {1 {:surname "Sparks"
                                            :firstname "Malcolm"
                                            :phone "1234"}
                                         2 {:surname "Pither"
                                            :firstname "Jon"
                                            :phone "1235"}}}))
   {}))

(use-fixtures :each (with-system-fixture new-system))
(use-fixtures :once schema.test/validate-schemas)

(deftest system-sanity-test
  (given *system*
         [:phonebook :api routes] :!? nil?
         [:phonebook :api routes] :- RoutePair))

(def request-decorators
  [middleware/decorate-method
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
  (given
      @(http/post
        "http://localhost:9015/phonebook"
        {:pool test-connection-pool
         :follow-redirects false
         :headers {"content-type" "application/x-www-form-urlencoded"}
         :body (codec/form-encode {:firstname "Kath" :surname "Read" :phone "1236"})})
      :status := 303
      [:headers "location"] :# "/phonebook/\\d+"
      [:headers keys] :⊃ #{"content-length"}
      [:headers keys] :⊅ #{"content-type"}
      [:headers keys] :⊅ #{"vary"}))

#_(deftest put-test
  (given @(http/put "http://localhost:9015/phonebook/2" {:body (create-body 4)})
    :status := 204
    :body :!= nil
    )
  )

;; TODO: Would be great to be able to look up a handler by tag
;; Use route-seq

;;
#_(-> dev/system :phonebook :server :routes (bidi/match-route "/phonebook") :handler
    :methods :post :consumes)

#_(b/to-string (:body @(http/post
                      "http://localhost:8099/phonebook"
                      {:pool test-connection-pool
                       :headers {"content-type" "application/x-www-form-urlencoded"}
                       :body (codec/form-encode {:firstname "Kath"
                                                 :surname "Read"
                                                 :phone "1236"})})))
