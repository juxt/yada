(ns yada.suffixes
  (:require
   [yada.syntax :as syn]))

(def suffix-matcher-pattern
  (re-pattern
   (str (syn/as-regex-str syn/token) "/" (syn/as-regex-str syn/token) (format "\\+([%s]+)$" (syn/as-regex-str syn/ALPHA)))))

(defn- suffix [media-type]
  (when media-type
    (second (re-matches suffix-matcher-pattern media-type))))

(defmulti fragment-media-type suffix)

(defmethod fragment-media-type :default [_] nil)
(defmethod fragment-media-type "json" [_] "application/json")
(defmethod fragment-media-type "xml" [_] "application/xml")
(defmethod fragment-media-type "wbxml" [_] "application/vnd.wap.wbxml")
(defmethod fragment-media-type "zip" [_] "application/zip")
