;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler route-seq succeed unmatch-pair unroll-route)]
   [bidi.ring :refer (Ring request make-handler)]
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
   [yada.methods :refer (Get get*)]
   [yada.mime :as mime]
   [yada.resource :refer (Resource ResourceRepresentations ResourceCoercion platform-charsets make-resource) :as res]
   [yada.core :as yada])
  (:import (clojure.lang PersistentVector Keyword)))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String (encode [s] s)
  PersistentVector (encode [v] (apply str (map encode v)))
  Keyword (encode [k] (str "{" (name k) "}")))

(defn- to-path [route]
  (let [path (->> route :path (map encode) (apply str))
        http-resource (-> route :handler :delegate)
        {:keys [resource options methods parameters representations]} http-resource
        swagger (:swagger options)]
    [path
     (merge-with merge
                 (into {}
                       (for [method methods
                             :let [parameters (get parameters method)
                                   representations (filter (fn [rep] (or (nil? (:method rep))
                                                                        (contains? (:method rep) method))) representations)
                                   produces (map mime/media-type (mapcat (comp :content-type) representations))]]
                         ;; Responses must be added in the static swagger section
                         {method {:produces produces
                                  :parameters parameters}}))
                 swagger)]))

(defrecord SwaggerSpec [spec created-at]
  Resource
  (methods [_] #{:get :head})
  (exists? [_ ctx] true)
  (last-modified [_ ctx] created-at)

  ResourceRepresentations
  (representations [_]
    [{:content-type #{"application/json"
                      "application/json;pretty=true"
                      "text/html;q=0.9"
                      "application/edn;q=0.8"}
      :charset platform-charsets}])

  Get
  (get* [_ ctx] (rs/swagger-json spec)))

(defrecord Swaggered [spec route spec-handler]
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
      (unmatch-pair route m)))

  Ring
  (request [_ req match-context]
    ;; This yada resource has match-context in its lexical scope,
    ;; containing any yada/partial (or bidi/partial) entries.
    (spec-handler req))

  ;; So that we can use the Swaggered record as a Ring handler
  clojure.lang.IFn
  (invoke [this req]
    (let [handler (make-handler ["" this])]
      (handler req))))

(defn swaggered [spec route]
  (let [spec (merge spec {:paths (into {} (map to-path (route-seq (unroll-route route))))})]
    (->Swaggered spec route
                 (yada/resource (->SwaggerSpec spec (to-date (now)))))))
