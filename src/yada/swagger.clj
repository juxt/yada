;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed)]
   [bidi.ring :refer (Ring)]
   [camel-snake-kebab :as csk]
   [cheshire.core :as json]
   [clj-time.coerce :refer (to-date)]
   [clj-time.core :refer (now)]
   [clojure.pprint :refer (pprint)]
   [clojure.tools.logging :refer :all]
   [hiccup.page :refer (html5)]
   [json-html.core :as jh]
   [ring.swagger.swagger2 :as rs]
   [ring.util.response :refer (redirect)]
   [schema.core :as s]
   [yada.bidi :refer (resource-leaf)]
   [yada.methods :refer (Get get*)]
   [yada.mime :as mime]
   [yada.resource :refer (Resource ResourceRepresentations ResourceConstructor platform-charsets make-resource) :as res])
  (:import (clojure.lang PersistentVector Keyword)))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String (encode [s] s)
  PersistentVector (encode [v] (apply str (map encode v)))
  Keyword (encode [k] (str "{" (name k) "}")))

(defn- to-path [x]
  (let [swagger (-> x :handler :options :swagger)
        options (-> x :handler :options)
        resource (make-resource (-> x :handler :resource))
        methods (or (:methods options) (res/methods resource))]
    [(apply str (map encode (:path x)))
     (merge-with merge swagger
                 (into {}
                       (for [method methods]
                         ;; TODO: Add parameters
                         ;; TODO: Add responses
                         {method {:description "a method"}})))]))

(defrecord SwaggerSpec [spec created-at]
  Resource
  (methods [_] #{:get :head})
  ;; TODO: Parameters should be optional, so use a protocol like ResourceParameters
  (exists? [_ ctx] true)
  (last-modified [_ ctx] created-at)

  ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
      :content-type #{"application/json" "text/html;q=0.9" "application/edn;q=0.8"}
      :charset platform-charsets}])

  Get
  (get* [_ ctx] (rs/swagger-json spec)))

(defrecord Swagger [spec route handler]
  Matched
  (resolve-handler [this m]
    (cond (= (:remainder m) (str (or (:base-path spec) "") "/swagger.json"))
          ;; Return this, which satisfies Ring.
          ;; Truncate :remainder to ensure succeed actually succeeds.
          (succeed this (assoc m :remainder ""))

          ;; Redirect to swagger.json
          (= (:remainder m) (str (or (:base-path spec) "") "/"))
          (succeed (reify Ring (request [_ req _] (redirect (str (:uri req) "swagger.json"))))
                   (assoc m :remainder ""))

          ;; Otherwise
          :otherwise (resolve-handler [[(or (:base-path spec) "") [route]]]
                                      (merge m {::spec spec}))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      (or (:base-path spec) "")
      (unresolve-handler (:paths spec) m)))

  Ring
  (request [_ req match-context]
    ;; This yada resource has match-context in its lexical scope,
    ;; containing any yada/partial (or bidi/partial) entries.
    (handler req)))

(defn swaggered [spec route]
  (let [spec (merge spec {:paths (into {} (map to-path (route-seq route)))})]
    (->Swagger spec route (resource-leaf (->SwaggerSpec spec (to-date (now)))))))
