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
  (get-in ctx [:response :produces :language]))

(defn- segments [s]
  (let [l (re-seq #"[^/]*/?" s)]
    (if (.endsWith s "/") l (butlast l))))

(defn relativize [from to]
  (if (and from to)
    (loop [from (segments from)
           to (segments to)]
      (if-not (= (first from) (first to))
        (str (apply str (repeat (+ (dec (count from))) "../"))
             (apply str to))
        (if (next from)
          (recur (next from) (next to))
          (first to))))
    to))

(defn uri-for [ctx handler & [options]]
  (if-let [uri-for (:uri-for ctx)]
    (when-let [res (uri-for handler options)]
      (-> res
          (update :href (fn [href]
                          (when href
                            (if (.startsWith href "/")
                              (relativize (-> ctx :request :uri) href)
                              href))))))
    (throw (ex-info "Context does not contain a :uri-for entry" ctx))))


