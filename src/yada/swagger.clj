(ns yada.swagger
  (:require
   [clojure.pprint :refer (pprint)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed)]
   [bidi.ring :refer (Ring)]
   [yada.bidi :refer (resource)]
   [camel-snake-kebab :as csk]
   [cheshire.core :as json])
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

#_(defn squash [m]
  (let [m (into {} (filter second m))]
    (when (not-empty m) m)))

#_(defn extract-ops [resource-map]
  (squash
   {:get (squash
          (let [o (some-> resource-map :body)]
            {:summary (or (some-> o meta :summary) "")
             :description (or (some-> o meta :description) "")}))
    :post nil}))

#_(extract-ops {:body ^{:summary "hi"} (fn [ctx] "Hello")})

#_(defn- to-path [x]
  [(apply str (map encode (:path x)))
   (let [resource-map (-> x :handler :resource-map)]
     (into {}
           (for [[k v] (or
                        ;; allowed-methods is deprecated
                        #_(:allowed-methods resource-map)
                        (extract-ops resource-map))]
             [k {:summary (:summary v)
                 :description (:description v)
                 :parameters []}])))])

;; TODO: Now extract the parameters declarations!

(defn- to-path [x]
  (let [swagger (-> x :handler meta :swagger)]
    [(apply str (map encode (:path x)))
     swagger
     ]))

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
      :body (json/encode
             (merge {:swagger "2.0"} spec {:paths (into {} (map to-path (route-seq ["" routes])))})
             {:pretty true
              :key-fn (fn [x] (csk/->camelCase (name x)))}))
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
