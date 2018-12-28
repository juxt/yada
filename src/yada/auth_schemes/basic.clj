(ns yada.auth-schemes.basic
  (:require
   [clojure.data.codec.base64 :as base64]
   [clojure.string :as str]
   [yada.security :refer [scheme-default-parameters verify]]))

;; tag::verify[]
(defmethod verify "Basic" [ctx {:keys [verify]}]              ; <1>
  (let [auth (get-in ctx [:request :headers "authorization"]) ; <2>
        cred (and auth (apply str (map char (base64/decode (.getBytes ^String (last (re-find #"^Basic (.*)$" auth)))))))] ; <3>
    (when cred
      (let [[user password] (str/split (str cred) #":" 2)]
        (verify [user password])        ; <4>
        ))))
;; end::verify[]

(defmethod scheme-default-parameters "Basic" [ctx {:keys [realm]}]
  ;; > The "realm" authentication parameter is reserved for use by
  ;; > authentication schemes that wish to indicate a scope of
  ;; > protection.
  ;; -- RFC 7235 Section 2.2
  ;;
  ;; In the case of Basic, realm is REQUIRED, charset it OPTIONAL. We
  ;; choose to send charset anyway (users can override this decision
  ;; by overriding this defmethod).
  {:realm realm
   ;; > The only allowed value is "UTF-8";
   ;; -- RFC 7617 Section 2.1
   :charset "UTF-8"})
