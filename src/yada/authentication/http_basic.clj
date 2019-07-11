(ns yada.authentication.http-basic
  (:require
   [clojure.data.codec.base64 :as base64]
   [yada.authentication :as ya]
   [yada.context :as ctx]
   [yada.syntax :as syn]
   [clojure.tools.logging :as log]
   [yada.yada :as yada]))

(def CTL-set (set (map byte (identity syn/CTL))))

(defn- parse-credentials
  [credentials]
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

(defn http-basic-authenticator
  "Return a yada authenticator. The first argument is a 4-arity function
  which takes the ctx, user, password and attributes. It should return
  truthy if authenticated, nil if not. Returning false ensures no
  other authenticators are tried. The second argument is attributes that
  must contain a :yada.authentication/realm entry. This is passed unchanged to
  the given authenticate function."
  [authenticate attributes]
  (assert (fn? authenticate))
  (let [realm (get attributes "realm")]
    (assert realm "http-basic-authenticator must have a realm attribute")
    {::ya/scheme "Basic"
     ::ya/attributes attributes
     ::ya/authenticate (fn [ctx]
                         (let [[user password] (parse-credentials (ctx/authorization ctx))]
                           (authenticate ctx user password attributes)))
     ::ya/challenge (fn [ctx] {:params (merge {"charset" "UTF-8"} attributes)})
     ::ya/challenge-order 0}))
