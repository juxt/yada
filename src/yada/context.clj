;; Copyright © 2014-2017, JUXT LTD.

(ns yada.context
  (:require
   [clojure.tools.logging :refer :all]))

(defrecord Response [])

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
