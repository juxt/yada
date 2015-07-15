;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [clojure.pprint :refer (pprint)]
   [clj-time.core :refer (now)]
   [clj-time.coerce :refer (to-date)]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed)]
   [bidi.ring :refer (Ring)]
   [ring.util.response :refer (redirect)]
   [yada.bidi :refer (resource-leaf)]
   [yada.resource :refer (Resource ResourceRepresentations ResourceConstructor platform-charsets)]
   [yada.mime :as mime]
   [clojure.tools.logging :refer :all]
   [camel-snake-kebab :as csk]
   [cheshire.core :as json]
   [ring.swagger.swagger2 :as rs]
   [schema.core :as s]
   [json-html.core :as jh]
   [hiccup.page :refer (html5)])
  (:import (clojure.lang PersistentVector Keyword)))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String (encode [s] s)
  PersistentVector (encode [v] (apply str (map encode v)))
  Keyword (encode [k] (str "{" (name k) "}")))

(defn- to-path [x]
  (let [swagger (-> x :handler :options :swagger)
        parameters (-> x :handler :options :parameters)]
    [(apply str (map encode (:path x)))
     (merge-with merge swagger
                 (into {}
                       (for [[k v] parameters]
                         [k {:parameters v}]
                         )))]))

(defrecord SwaggerSpec [spec created-at]
  ResourceConstructor
  (make-resource [o] o)

  Resource
  (methods [_] #{:get :head})
  (parameters [_] nil)
  (exists? [_ ctx] true)
  (last-modified [_ ctx] created-at)
  (request [_ method ctx] (case method :get (rs/swagger-json spec)))

  ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
       :content-type #{"application/json" "text/html;q=0.9" "application/edn;q=0.8"}
       :charset platform-charsets}]))

(defrecord Swagger [spec routes handler]
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
          :otherwise (resolve-handler [[(or (:base-path spec) "") routes]]
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
    (->Swagger spec routes (resource-leaf (->SwaggerSpec spec (to-date (now)))))))
