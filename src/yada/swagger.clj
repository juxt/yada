;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [bidi.bidi :refer [Matched resolve-handler unresolve-handler route-seq succeed unmatch-pair]]
   [bidi.ring :refer [Ring request make-handler]]
   [camel-snake-kebab :as csk]
   [cheshire.core :as json]
   [clj-time.coerce :refer [to-date]]
   [clj-time.core :refer [now]]
   [clojure.set :as set]
   [clojure.pprint :refer [pprint]]
   [clojure.tools.logging :refer :all]
   [hiccup.page :refer [html5]]
   [json-html.core :as jh]
   [ring.swagger.swagger2 :as rs]
   [ring.swagger.swagger2-schema :as rss]
   [ring.util.response :refer [redirect]]
   [schema.core :as s]
   [schema.coerce :as sc]
   [yada.charset :as charset]
   [yada.media-type :as mt]
   [yada.protocols :as p]
   [yada.resource :refer [resource]]
   [yada.core :as yada :refer [yada]]
   [yada.util :refer [md5-hash] :as util])
  (:import [clojure.lang PersistentVector Keyword]))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String (encode [s] s)
  PersistentVector (encode [v] (apply str (map encode v)))
  Keyword (encode [k] (str "{" (name k) "}")))

(def media-type-names
  (comp (map (comp :name :media-type))
        (distinct)))

(sequence media-type-names [{:media-type {:name "text/html"}}
                            {:media-type {:name "foo"}}
                            {:media-type {:name "foo"}}])

(defn to-path [route]
  (let [path (->> route :path (map encode) (apply str))
        handler (-> route :handler)
        {:keys [resource methods parameters produces consumes]} handler
        ]

    [path
     (into {}
           (for [m (keys methods)
                 :let [{:keys [description summary] :as method}
                       (get-in handler [:methods m])

                       parameters (some->> (:parameters method)
                                           (util/merge-parameters parameters))
                       produces (some->> (:produces method)
                                         (concat produces)
                                         (sequence media-type-names)) 
                       consumes (some->> (:consumes method)
                                         (concat consumes)
                                         (sequence media-type-names))]]
             
             ;; Responses must be added in the static swagger section
             {m (merge
                 (when description {:description description})
                 (when summary {:summary summary})
                 (when (not-empty parameters) {:parameters parameters})
                 (when (not-empty produces) {:produces produces})
                 (when (not-empty consumes) {:consumes consumes}))}))]))

(defn create-spec [template routes]
  (merge template
         {:paths (into {} (map to-path (route-seq routes)))}))

(def ^{:doc "To achieve compatibility with ring-swagger as per
  ring.swagger.swagger2-schema"} ring-swagger-coercer
  (sc/coercer rss/Swagger {rss/Parameters #(set/rename-keys % {:form :formData})}))

(defn swagger-spec-resource [spec created-at content-type properties]
  (resource
   {:properties (merge {:last-modified created-at
                        :version (md5-hash (pr-str spec))}
                       properties)
    :produces
    (case content-type
      "application/json" [{:media-type #{"application/json"
                                         "application/json;pretty=true"}
                           :charset #{"UTF-8" "UTF-16;q=0.9" "UTF-32;q=0.9"}}]
      "text/html" [{:media-type "text/html"
                    :charset charset/platform-charsets}]
      "application/edn" [{:media-type #{"application/edn"
                                        "application/edn;pretty=true"}
                          :charset #{"UTF-8"}}])

    :methods {:get {:handler (fn [ctx] spec)}}}))

(defrecord Swaggered [spec routes spec-handlers]
  Matched
  (resolve-handler [this m]
    (cond (= (:remainder m) (str (or (:base-path spec) "") "/swagger.json"))
          ;; Return this, which satisfies Ring.
          ;; Truncate :remainder to ensure succeed actually succeeds.
          (succeed this (assoc m :remainder "" :type "application/json"))

          (= (:remainder m) (str (or (:base-path spec) "") "/swagger.edn"))
          (succeed this (assoc m :remainder "" :type "application/edn"))

          (= (:remainder m) (str (or (:base-path spec) "") "/swagger.html"))
          (succeed this (assoc m :remainder "" :type "text/html"))

          ;; Redirect to swagger.json
          (= (:remainder m) (str (or (:base-path spec) "") "/"))
          (succeed (reify Ring (request [_ req _] (redirect (str (:uri req) "swagger.json"))))
                   (assoc m :remainder ""))

          ;; Otherwise
          :otherwise
          (resolve-handler [[(or (:base-path spec) "") [routes]]]
                           (merge m {::spec spec}))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      (or (:base-path spec) "")
      (unmatch-pair routes m)))

  Ring
  (request [_ req match-context]
    (if-let [h (get spec-handlers (:type match-context))]
      (h req)
      (throw (ex-info "Error: unknown type" {:match-context match-context}))))

  ;; So that we can use the Swaggered record as a Ring handler
  clojure.lang.IFn
  (invoke [this req]
    (let [handler (make-handler ["" this])]
      (handler req))))

(defn swaggered [template routes & [properties]]
  (let [spec (-> (create-spec template routes) ring-swagger-coercer rs/swagger-json)]
    (map->Swaggered
     {:spec spec
      :routes routes
      :spec-handlers
      (into {}
            (for [ct ["application/edn" "application/json" "text/html"]]
              [ct (yada (swagger-spec-resource spec (to-date (now)) ct properties))]))})))
