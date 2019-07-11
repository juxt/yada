;; Copyright © 2014-2017, JUXT LTD.

(ns yada.context
  (:require
   [clojure.tools.logging :refer :all]
   [yada.syntax :as syn]))

(defrecord Response [])

(defrecord Context [response])

(defn make-context []
  (map->Context {:response (assoc (->Response) :headers {})
                 ::cache (atom {})}))

(defn exists?
  "We assume every resource exists unless it says otherwise, with an
  explicit exists? entry in its properties."
  [ctx]
  (let [props (:properties ctx)]
    (if (contains? props :exists?)
      (:exists? props)
      true)))

;; Convenience functions, allowing us to encapsulate the context
;; structure.
(defn content-type [ctx]
  (get-in ctx [:response :produces :media-type :name]))

(defn charset [ctx]
  (get-in ctx [:response :produces :charset :alias]))

(defn language [ctx]
  (apply str (interpose "-" (get-in ctx [:response :produces :language :language]))))

(defn path-parameter [ctx n]
  (get-in ctx [:parameters :path n]))

(defn query-parameter [ctx n]
  (get-in ctx [:parameters :query n]))

(defn uri-info [ctx handler & [options]]
  (if-let [uri-info (:uri-info ctx)]
    (uri-info handler options)
    (throw (ex-info "Context does not contain a :uri-info entry" {:keys (keys ctx)}))))

(def ^:deprecated uri-for uri-info)

(def path-for (comp :path uri-info))
(def host-for (comp :host uri-info))
(def scheme-for (comp :scheme uri-info))
(def href-for (comp :href uri-info))
(def url-for (comp :uri uri-info))


;; Cache functions

;; The intention behind these cache functions is to make potentially
;; expensive computations more efficient, specifically parsing request
;; header values.. The yada context is a per-request data structure,
;; so caching on the context does not affect other requests.

(defn unless-cached-compute [ctx k f]
  (let [cache (::cache ctx)]
    (if-let [[_ hit] (find @cache k)]
      hit
      (get (swap! cache assoc k (f)) k))))

(defn authorization
  "Return a parsed authorization"
  [ctx]
  (unless-cached-compute
   ctx ::authorization
   #(syn/parse-credentials (get-in ctx [:request :headers "authorization"]))))

;; It is intended that many other headers will eventually undergo more
;; serious parsing and error checks, and therefore want to make use of
;; this cache capability.
