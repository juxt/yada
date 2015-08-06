;; Copyright © 2015, JUXT LTD.

(ns yada.resource-test
  (:require
   [yada.resource :refer [coerce-etag-result]]
   [clojure.test :refer :all]
   [juxt.iota :refer [given]]))

(deftest coerce-etag-result-test
  (is (= (coerce-etag-result "123") "123"))
  (is (= (coerce-etag-result {:foo :bar}) "1766419479"))
  (is (= (coerce-etag-result [:foo :bar]) "1531896286")))

;; TODO: Serve up file images and other content required for the examples with yada - don't use bidi/resources

;; TODO: yada should replace bidi's resources, files, etc.  and do a far better job
;; TODO: Observation: wrap-not-modified works on the response only - i.e. it still causes the handler to handle the request (and do work) on the server.

;; A resource does not map directly onto a database table.
;; But it can, often, map onto a document in a NoSQL datastore

;; Schema is the defining aspect of a resource's model.
;; Unlike XML Schema, it is not concerned with representation, only the model

;; ---------------------------------------------------------------

;; State is sometimes real, like a file, sometimes merely conceptual.
;; State of the latter variety needs support

;; We define state with schema, which shapes the state but defining its constraints and boundaries.

;; Protocols are the answer here
;;

;; Loadable - state can be loaded
;; Storable

;; A Clojure record represents the state

;; In another namespace, e.g. kv-store, we extend the State type with Loadable and Saveable

#_(defprotocol Storage
  (store-state! [_ s] "Put the state into storage")
  (fetch-state [_] "Get the state from storage"))

;; A file defines both its state and its storage
#_(extend-type File
  Storage
  (store-state! [f s] (println "Writing to f new contents s"))
  (fetch-state [f] f))

;; Work on java.io.File first, then clojure.lang.Atom, then integrate schema-enforced maps


;; I suppose a file can also be a sql-lite database file, backing a resource. It should be OK to have a sql-lite file per resource set, e.g. /customers /customers/1234 - so could be set in a yada/partial

#_(defrecord StoredState []
  Storage
  (store-state! [_ state] (println "Putting state" state))

  ;; Should support range requests, query parameters, partial query parameters?
  (fetch-state [_] (println "Getting state") {:username "bob" :name "Bob"})
  )

#_(let [resource-map
      {:state (StoredState.)}]

  ;; A PUT request arrives on a new URL, containing a representation which is parsed into the following model :-
    (let [state {:username "alice" :name "Alice"}]
    ;; which is then stored
    (store-state! (:state resource-map) state)))


;; State is always at rest, when state is in-flight, it exists merely as a representation
;; A clojure map is a _representation_, albeit a flexible one with respect to serialization (whereas an atom containing the clojure map would be _state_)

;; Design goal: the ability to have a file returned to aleph intact
;; Design goal: the ability to do this: (yada file), (yada dir),
;; (yada "target")
;; (yada ".") would mean serve working directory
;; and yada looks like a file-server

;; get away from resource-maps as being the only way to call yada, but always have the option to convert between them (user generates a resource map and converts to a yada handler, or constructs a yada handler and extracts a resource map)
