;; Copyright © 2015, JUXT LTD.

(ns yada.swagger
  (:require
   [bidi.bidi :refer [Matched resolve-handler unresolve-handler route-seq succeed unmatch-pair]]
   [bidi.ring :refer [Ring request make-handler]]
   [camel-snake-kebab.core :as csk]
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
   [yada.handler :refer [handler]]
   [yada.body :refer [as-body]]
   [yada.charset :as charset]
   [yada.media-type :as mt]
   [yada.protocols :as p]
   [yada.resource :refer [resource]]
   [yada.resources.classpath-resource :refer [new-classpath-resource]]
   [yada.schema :as ys]
   [yada.util :refer [md5-hash] :as util])
  (:import [clojure.lang PersistentVector Keyword]
           [yada.handler Handler]
           [yada.resource Resource]))

(defprotocol SwaggerPath
  (encode [_] "Format route as a swagger path"))

(extend-protocol SwaggerPath
  String (encode [s] s)
  PersistentVector (encode [v] (apply str (map encode v)))
  Keyword (encode [k] (str "{" (name k) "}")))

(def media-type-names
  (comp (map (comp :name :media-type))
        (distinct)))

;; We can use resources as handlers directly, or wrap in handlers. We
;; must be careful to be able to work with both.
(defprotocol HandlerToResource
  (handler->resource [_] "Return a resource from a handler"))

(extend-protocol HandlerToResource
  Handler
  (handler->resource [h] (:resource h))
  Resource
  (handler->resource [r] r))

(defn to-path [route]
  (let [path (->> route :path (map encode) (apply str))
        {:keys [methods parameters produces consumes]} (handler->resource (:handler route))]
    [path
     (into {}
           (for [m (keys methods)
                 :let [{:keys [description summary] :as method}
                       (get methods m)

                       parameters (-> parameters
                                      (util/merge-parameters (:parameters method))
                                      (dissoc :cookie))
                       produces (->> (:produces method)
                                     (concat produces)
                                     (sequence media-type-names)) 
                       consumes (->> (:consumes method)
                                     (concat consumes)
                                     (sequence media-type-names))]]
             
             ;; Responses must be added in the static swagger section
             {m (merge
                 (when description {:description description})
                 (when summary {:summary summary})
                 (when (not-empty parameters) {:parameters parameters})
                 (when (not-empty produces) {:produces produces})
                 (when (not-empty consumes) {:consumes consumes}))}))]))

(def ^{:doc "To achieve compatibility with ring-swagger as per
  ring.swagger.swagger2-schema"} ring-swagger-coercer
  (sc/coercer rss/Swagger {rss/Parameters #(set/rename-keys % {:form :formData})}))

(defn routes->ring-swagger-spec [routes & [template]]
  (-> (or template {})
      (merge {:paths (into {} (map to-path (route-seq routes)))})
      ring-swagger-coercer))

(defn swagger-spec [routes template & [content-type]]
  (-> routes
      (routes->ring-swagger-spec template)
      rs/swagger-json))

(defrecord SwaggerSpecResource [spec content-type]
  p/ResourceCoercion
  (as-resource [_]
    (resource
     {:properties {:last-modified (to-date (now))
                   :version (md5-hash (pr-str spec))}
      :produces
      (case (or content-type "application/json")
        "application/json" [{:media-type #{"application/json"
                                           "application/json;pretty=true"}
                             :charset #{"UTF-8" "UTF-16;q=0.9" "UTF-32;q=0.9"}}]
        "text/html" [{:media-type "text/html"
                      :charset charset/platform-charsets}]
        "application/edn" [{:media-type #{"application/edn"
                                          "application/edn;pretty=true"}
                            :charset #{"UTF-8"}}])

      ;; We must wrap the spec, otherwise body would get treated as
      ;; part of the resource definition.
      :methods {:get (as-body spec)}})))

(defn swagger-spec-resource [swagger-spec & [content-type]]
  (->SwaggerSpecResource swagger-spec content-type))

;; Convenience

(defrecord Swaggered [spec routes spec-handlers swagger-ui]
  Matched
  (resolve-handler [this m]
    (cond (= (:remainder m) "/swagger.json")
          ;; Return this, which satisfies Ring.
          ;; Truncate :remainder to ensure succeed actually succeeds.
          (succeed this (assoc m :remainder "" :type "application/json"))

          (= (:remainder m) "/swagger.edn")
          (succeed this (assoc m :remainder "" :type "application/edn"))

          (= (:remainder m) "/swagger.html")
          (succeed this (assoc m :remainder "" :type "text/html"))

          (.startsWith (:remainder m) "/swagger")
          {:handler this
           :ui true
           :remainder (subs (:remainder m) (count "/swagger"))}

          ;; Redirect to swagger.json
          (#{"" "/"} (:remainder m))
          (succeed
           (reify Ring
             (request [_ req _]
               (redirect (str (:uri req)
                              (subs "/swagger/index.html?url=" (count (:remainder m)))
                              (or (:basePath spec) "") "/swagger.json"))))
           (assoc m :remainder ""))

          ;; Otherwise
          :otherwise
          (resolve-handler [["" [routes]]]
                           (merge m {::spec spec}))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      (or (:basePath spec) "")
      (unmatch-pair routes m)))

  Ring
  (request [_ req match-context]
    (if (:ui match-context)
      (swagger-ui (assoc req :path-info (:remainder match-context)))
      (if-let [h (get spec-handlers (:type match-context))]
        (h req)
        (throw (ex-info "Error: unknown type" {:match-context match-context})))))

  ;; So that we can use the Swaggered record as a Ring handler
  clojure.lang.IFn
  (invoke [this req]
    (let [handler (make-handler ["" this])]
      (handler req))))

(defn swaggered [routes & [template]]
  (let [spec (swagger-spec routes (or template {}))]
    (map->Swaggered
     {:spec spec
      :routes routes
      :swagger-ui (handler (new-classpath-resource "META-INF/resources/webjars/swagger-ui/2.1.3")) 
      :spec-handlers
      (into {}
            (for [ct ["application/edn" "application/json" "text/html"]]
              [ct (handler (swagger-spec-resource spec ct))]))})))

