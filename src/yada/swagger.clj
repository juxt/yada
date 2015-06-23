;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [clojure.pprint :refer (pprint)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed)]
   [bidi.ring :refer (Ring)]
   [yada.bidi :refer (resource-leaf)]
   [yada.resource :refer (Resource)]
   [yada.mime :as mime]
   [clojure.tools.logging :refer :all]
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

(defn- to-path [x]
  (let [swagger (-> x :handler meta :swagger)
        parameters (-> x :handler :options :parameters)]
    [(apply str (map encode (:path x)))
     (merge-with merge swagger
              (into {}
                    (for [[k v] parameters]
                      [k {:parameters v}]
                      )))]))

(defrecord SwaggerSpec [spec created-at]
  Resource
  (produces [_ ctx] #{"application/json"})
  (produces-charsets [_ ctx] #{"UTF-8"})
  (exists? [_ ctx] true)
  (last-modified [_ ctx] created-at)
  (get-state [_ content-type ctx]
    (when (= (mime/media-type content-type) "application/json")
      (json/encode (rs/swagger-json spec))))
  (content-length [_ ctx] nil))

(defrecord Swagger [spec routes handler]
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

    (handler
     req)))

(defn swaggered [spec routes]
  (let [spec (merge spec {:paths (into {} (map to-path (route-seq ["" routes])))})]
    (->Swagger spec routes (resource-leaf (->SwaggerSpec spec (java.util.Date.))))))
