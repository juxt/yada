(ns yada.authentication.http-bearer
  (:require
   [clojure.data.codec.base64 :as base64]
   [yada.authentication :as ya]
   [yada.context :as ctx]
   [yada.syntax :as syn]
   [clojure.tools.logging :as log]
   [yada.yada :as yada]))

;; See RFC 6750 for further details

(defn http-bearer-authenticator
  "Return a yada authenticator. The first argument is a 3-arity function
  which takes the ctx, token and options. It should return truthy if
  authenticated, nil if not; returning false ensures no other
  authenticators are tried. The second argument is attributes which may
  contain entries for :yada.authentication/realm and ::scope. These attributes are
  passed unchanged to the authenticator in the first argument."
  ([authenticate attributes]
   (assert (fn? authenticate))
   (merge
    attributes
    {::ya/scheme "Bearer"
     ::ya/authenticate (fn [ctx]
                         (authenticate ctx (::syn/value (ctx/authorization ctx)) attributes))
     ::ya/challenge (fn [ctx] {:params attributes})
     ::ya/challenge-order 10}))
  ([authenticate]
   (http-bearer-authenticator authenticate {})))
