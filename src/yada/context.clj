;; Copyright © 2015, JUXT LTD.

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

(defn uri-for [ctx handler & [options]]
  (if-let [uri-for (:uri-for ctx)]
    (uri-for handler options)
    (throw (ex-info "Context does not contain a :uri-for entry" ctx))))
