;; Copyright © 2015, JUXT LTD.

(ns yada.jwt
  (:require
   [buddy.sign.jws :as jws]
   [yada.security :refer [verify]])
  (:import [clojure.lang ExceptionInfo]))

(defmethod verify :jwt
  [ctx {:keys [cookie yada.jwt/secret] :or {cookie "session"} :as scheme}]
  (when-not secret (throw (ex-info "Buddy JWT verifier requires a secret entry in scheme" {:scheme scheme})))
  (try
    (let [auth (some->
                (get-in ctx [:cookies cookie])
                (jws/unsign secret))]
      auth)
    (catch ExceptionInfo e
      (if-not (= (ex-data e)
                 {:type :validation :cause :signature})
        (throw e)
        )
      )))


