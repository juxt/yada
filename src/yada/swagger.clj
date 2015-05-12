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
  (encode [_] "Encode path as a swagger path"))

(extend-protocol SwaggerPath
  String
  (encode [s] s)
  PersistentVector
  (encode [v] (apply str (map encode v)))
  Keyword
  (encode [k] (str "{" (name k) "}")))

(defn- to-path [x]
  [(apply str (map encode (:path x)))
   (let [resource-map (-> x :handler :resource-map)]
     (into {}
           (for [[k v] (or (:allowed-methods resource-map)
                           ;; TODO: Is this really needed? can yada do the inferences?
                           {:get "GET"})]
             [k {:summary v
                 :description "A description"
                 :parameters []}])))])

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
