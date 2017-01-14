;; Copyright © 2014-2017, JUXT LTD.

(ns ^{:doc
      "Based on an original recipe ring.middleware.cookies, my own
    includes chocolate-chip coercions."}
    yada.cookies
  (:require
   [clj-time.coerce :as time]
   [clj-time.format :as tf]
   [clojure.string :as str]
   [schema.core :as s]
   [schema.coerce :as sc]))

(s/defschema Rfc822String (s/pred string?))

(s/defschema CookieValue
  {:value s/Str
   (s/optional-key :expires) Rfc822String
   (s/optional-key :max-age) s/Str ; TODO: support Interval like ring's cookies?
   (s/optional-key :domain) s/Str
   (s/optional-key :path) s/Str
   (s/optional-key :secure) s/Bool
   (s/optional-key :http-only) s/Bool})

(s/defschema Cookies
  {s/Str CookieValue})

(def CookieMappings
  {CookieValue (fn [x] (if (string? x) {:value x} x))
   Rfc822String (fn [x] (tf/unparse (tf/formatters :rfc822) (time/from-date (time/to-date x))))})

(def cookies-coercer
  (sc/coercer Cookies CookieMappings))

(def set-cookie-attrs
  {:domain "Domain", :max-age "Max-Age", :path "Path"
   :secure "Secure", :expires "Expires", :http-only "HttpOnly"})

(defn encode-attributes [cv]
  (apply str
         (for [k [:expires :max-age :path :domain :secure :http-only]]
           (when-let [v (get cv k)]
             (if (#{:secure :http-only} k)
               (format "; %s" (set-cookie-attrs k))
               (format "; %s=%s" (set-cookie-attrs k) v))))))

(s/defn encode-cookie :- s/Str
  [[k v]]
  (format "%s=%s%s" k (:value v) (encode-attributes v)))

(s/defn encode-cookies :- [s/Str]
  [cookies :- Cookies]
  (map encode-cookie cookies))

;; These taken from ring.util.parsing

(def re-token #"[!#$%&'*\-+.0-9A-Z\^_`a-z\|~]+")

(def re-quoted #"\"(\\\"|[^\"])*\"")

(def re-value (str re-token "|" re-quoted))

;; These taken from ring.middleware.cookies

(def re-cookie-octet
  #"[!#$%&'()*+\-./0-9:<=>?@A-Z\[\]\^_`a-z\{\|\}~]")

(def re-cookie-value
  (re-pattern (str "\"" re-cookie-octet "*\"|" re-cookie-octet "*")))

(def re-cookie
  (re-pattern (str "\\s*(" re-token ")=(" re-cookie-value ")\\s*[;,]?")))

(defn- parse-cookie-header
  "Turn a HTTP Cookie header into a list of name/value pairs."
  [header]
  (for [[_ name value] (re-seq re-cookie header)]
    [name value]))

(defn- strip-quotes
  "Strip quotes from a cookie value."
  [value]
  (str/replace value #"^\"|\"$" ""))

(defn- decode-values [cookies]
  (for [[name value] cookies]
    (when-let [value (strip-quotes value)]
      [name value])))

(defn parse-cookies
  "Parse the cookies from a request map."
  [request]
  (when-let [cookie (get-in request [:headers "cookie"])]
    (->> cookie
         parse-cookie-header
         ((fn [c] (decode-values c)))
         (remove nil?)
         (into {}))))
