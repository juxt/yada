;; Copyright © 2015, JUXT LTD.

(ns yada.multipart-http-test
  (:require
   [aleph.http :as http]
   [bidi.bidi :refer (match-route routes)]
   [bidi.ring :refer (make-handler)]
   [bidi.schema :refer [RoutePair]]
   [byte-streams :as b]
   [juxt.iota :refer (given) ]
   [clojure.test :refer :all]
   [com.stuartsierra.component :refer (system-using system-map)]
   [manifold.stream :as ms]
   [modular.aleph :refer (new-webserver)]
   [modular.test :refer (with-system-fixture *system*)]
   [phonebook.system :refer (new-phonebook)]
   [schema.core :as s]

   schema.test))

(def boundary "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")

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

#_(deftest put-test
  (given @(http/put "http://localhost:9015/phonebook/2" {:body (create-body 4)})
    :status := 204
    :body :!= nil
    )
  )

#_(deftest multipart-formdata-test
  (http/put "http://localhost:9015/phonebook")
  )
