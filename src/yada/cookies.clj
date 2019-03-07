;; Copyright © 2014-2017, JUXT LTD.

(ns
    ^{:doc "Based on an original recipe ring.middleware.cookies my own includes chocolate-chip coercions."}
    yada.cookies
  (:require
   [clj-time.coerce :as time]
   [clj-time.format :as tf]
   [clojure.string :as str]
   [schema.coerce :as sc]
   [schema.core :as s]
   [yada.syntax :as syn]
   yada.context)
  (:import
   (yada.context Context)))

(s/defschema Rfc822String (s/pred #(re-matches syn/rfc822-date-time %)))

;; The form a Set-Cookie should take prior to serialization
(s/defschema SetCookie
  {:value                              (s/pred #(re-matches syn/cookie-value %))
   (s/optional-key :expires)           (s/cond-pre s/Inst (s/pred #(instance? java.time.Duration %)) Rfc822String)
   (s/optional-key :max-age)           (s/cond-pre s/Str s/Int)
   (s/optional-key :domain)            (s/pred #(re-matches syn/subdomain %) "domain")
   (s/optional-key :path)              (s/pred #(re-matches syn/path %))
   (s/optional-key :secure)            s/Bool
   (s/optional-key :http-only)         s/Bool
   ;; technically this could also support a boolean which would default
   ;; to :strict, but let's be explicit about it
   (s/optional-key :same-site)         (s/enum :strict :lax)
   (s/constrained s/Keyword namespace) s/Any})

(s/defschema Cookies
  {s/Str SetCookie})

(def CookieMappings
  {SetCookie (fn [x] (if (string? x) {:value x} x))})

(def cookies-coercer
  (sc/coercer Cookies CookieMappings))

(def set-cookie-attrs
  {:domain    "Domain", :max-age "Max-Age", :path      "Path"
   :secure    "Secure", :expires "Expires", :http-only "HttpOnly"
   :same-site "SameSite"})

(defn encode-attributes [cv]
  (apply str
         (for [k [:domain :expires :http-only :max-age :path :same-site :secure]]
           (when-let [v (get cv k)]
             (case k
               (:secure :http-only)
               (format "; %s" (set-cookie-attrs k))

               :expires
               (format "; %s=%s" (set-cookie-attrs k)
                       (cond (inst? v) (tf/unparse (tf/formatters :rfc822) (time/to-date-time v))
                             (string? v) v
                             (instance? java.time.Duration v) (tf/unparse (tf/formatters :rfc822) (time/from-date (java.util.Date/from (.plus (java.time.Instant/now) v))))
                             :else (str v)))

               (format "; %s=%s" (set-cookie-attrs k) (name v)))))))

(defn encode-cookie
  [[k v]]
  (format "%s=%s%s" k (:value v) (encode-attributes v)))

(defn encode-cookies
  [cookies]
  (map encode-cookie cookies))

;; Interceptor
(defprotocol CookieConsumerResult
  (interpret-cookie-consumer-result [res ctx]))

(extend-protocol CookieConsumerResult
  Context
  (interpret-cookie-consumer-result [res _]
    res)

  nil
  (interpret-cookie-consumer-result [res ctx]
    ctx)

  Object
  (interpret-cookie-consumer-result [_ _]
    (throw (ex-info "Must return ctx" {}))))

;; Used to override a cookie
(defrecord Cookie [])

(defn set-cookie
  "Take a cookie defined in the resource and set it on the response."
  [ctx id val]

  (if-let [cookie-def (get-in ctx [:resource :cookies id])]
    (let [nm (:name cookie-def)]
      (update-in
       ctx [:response :cookies]
       (fnil conj {})
       [nm (s/validate
            SetCookie
            (merge
             (reduce-kv
              (fn [acc k v]
                (case k
                  :expires (assoc acc :expires (v ctx))
                  :max-age (assoc acc :max-age v)
                  :domain (assoc acc :domain v)
                  :path (assoc acc :path v)
                  :secure (assoc acc :secure v)
                  :http-only (assoc acc :http-only v)
                  :name acc
                  (if (namespace k) (assoc acc k v) acc)
                  ))
              {}
              cookie-def)
             (if (instance? Cookie val)
               val
               {:value (str val)})))]))

    (throw (ex-info (format "Failed to find declared cookie with id of '%s'" id) {}))))

(defn unset-cookie
  "Take a cookie defined in the resource and expire it"
  [ctx id]

  (if (string? id)
    (update-in ctx [:response :cookies]
               (fnil conj {})
               [id (s/validate
                    SetCookie
                    {:value "" :expires 0})])

    (if-let [cookie-def (get-in ctx [:resource :cookies id])]
      (let [nm (:name cookie-def)]
        (update-in ctx [:response :cookies]
                   (fnil conj {})
                   [nm (s/validate
                        SetCookie
                        {:value "" :expires 0})]))

      (throw (ex-info (format "Failed to find declared cookie with id of '%s'" id) {})))))

(defn parse-cookies [cookie-header-value]
  (->>
   cookie-header-value
   syn/parse-cookie-header
   (map (juxt ::syn/name ::syn/value))
   (into {})))

(defn resource-cookies [ctx]
  (reduce-kv
   (fn [acc id cookie-def]
     (assoc acc id (if (fn? cookie-def) (cookie-def ctx) cookie-def)))
   {}
   (-> ctx :resource :cookies)))

(defn ^:yada/interceptor consume-cookies [ctx]
  (let [request-cookies
        (parse-cookies (get-in ctx [:request :headers "cookie"]))

        process-cookies
        (fn [ctx resource-cookies]
          (reduce-kv
           (fn [ctx k resource-cookie]
             (let [n (:name resource-cookie)
                   cookie-val (get request-cookies n)]
               (if cookie-val
                 (let [consumer (:consumer resource-cookie)
                       pxy (fn [ctx cookie cookie-val]
                             (let [res (consumer ctx cookie cookie-val)]
                               (interpret-cookie-consumer-result res ctx)))]
                   (cond-> ctx consumer (pxy resource-cookie cookie-val)))
                 ;; No cookie-val, return ctx unchanged
                 ctx)))
           ctx
           resource-cookies))]

    (let [resource-cookies (resource-cookies ctx)]
      (cond-> ctx
        request-cookies (assoc :cookies request-cookies)
        resource-cookies (process-cookies resource-cookies)
        ))))
