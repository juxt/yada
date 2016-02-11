;; Copyright © 2015, JUXT LTD.

(ns yada.test
  (:require
   [byte-streams :as b]
   [bidi.ring :refer [make-handler]]))

(defn request-for [method uri & {:as options}]
  (merge
   {:server-port 80
    :server-name "localhost"
    :remote-addr "localhost"
    :uri uri
    :query-string nil
    :scheme :http
    :request-method method}
   (some-> options
           (:body options) (update :body b/to-byte-buffers)
           true (update :headers #(merge {"host" "localhost"} %)))))

(defn response-for [routes method uri & options]
  (let [h (make-handler routes)
        response @(h (apply request-for method uri options))]
    (cond-> response
      (:body response) (update :body b/to-string))))

