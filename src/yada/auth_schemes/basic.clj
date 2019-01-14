(ns yada.auth-schemes.basic
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.tools.logging :as log]
   [yada.syntax :as syn]
   [yada.security :refer [issue-challenge preprocess-authorization-header]]
   ))

(def CTL-set (set (map byte (identity syn/CTL))))

(defmethod issue-challenge "Basic"
  [ctx {:keys [scheme realm]}]
  {:scheme scheme
   :params (merge
            {:charset "UTF-8"}
            (when realm {:realm realm}))})

(defmethod preprocess-authorization-header "Basic"
  [ctx auth-scheme credentials]
  (if (not= (::syn/value-type credentials) :yada.syntax/token68)
    (do
      (log/infof "For credentials in the Basic authentication-scheme, the token68 syntax must be used")
      nil)

    (let [val (::syn/value credentials)
          bytes (seq (base64/decode (.getBytes val)))]
      (if (some CTL-set bytes)
        (do
          (log/infof "The user-id and password MUST NOT contain any control characters")
          nil)

        (let [[user [_colon & password]] (split-with #(not= % (byte \:)) bytes)]
          [(apply str (map char user))
           (apply str (map char password))])))))
