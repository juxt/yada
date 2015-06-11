;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [clojure.pprint :refer (pprint)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed)]
   [bidi.ring :refer (Ring)]
   [yada.bidi :refer (resource)]
   [camel-snake-kebab :as csk]
   [cheshire.core :as json]
   [ring.swagger.swagger2 :as rs]
   [schema.core :as s])
  (:import (clojure.lang PersistentVector Keyword)))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String
  (encode [s] s)
  PersistentVector
  (encode [v] (apply str (map encode v)))
  Keyword
  (encode [k] (str "{" (name k) "}")))

;; TODO: Now extract the produces section!

(defn- to-path [x]
  (let [swagger (-> x :handler meta :swagger)
        resource-map (-> x :handler :resource-map)
        ]
    [(apply str (map encode (:path x)))
     (merge-with merge swagger
              (into {}
                    (for [[k v] (:parameters resource-map)]
                      [k {:parameters v}]
                      )))]))

(defrecord Swagger [spec routes]
  Matched
  (resolve-handler [this m]
    (if (= (:remainder m) (str (or (:base-path spec) "") "/swagger.json"))
      ;; Return this, which satisfies Ring.
      ;; Truncate :remainder to ensure succeed actually succeeds.
      (succeed this (assoc m :remainder ""))
      ;; Otherwise
      (resolve-handler [[(or (:base-path spec) "") routes]]
                       (merge m {::spec spec}))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      (or (:base-path spec) "")
      (unresolve-handler (:paths spec) m)))

  Ring
  (request [_ req match-context]
    ;; This yada resource has match-context in its lexical scope,
    ;; containing any yada/partial (or bidi/partial) entries.
    ((resource
      (rs/swagger-json
              (merge spec {:paths (into {} (map to-path (route-seq ["" routes])))})))
     req)))

(defn swaggered [spec routes]
  (->Swagger spec routes))



(defrecord Thing [])

(defn thing [m]
  m
  (with-meta (map->Thing m) (meta m)))

(let [f (thing ^{:summary "a thing"} {})]
  (meta f)
  )
