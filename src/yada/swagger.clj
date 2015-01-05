;; Copyright © 2014 JUXT LTD.

(ns yada.swagger
  (:require
   [bidi.bidi :as bidi :refer (Matched resolve-handler unresolve-handler)]
   [bidi.ring :refer (Handle)]
   [hiccup.core :refer (html h)]
   [cheshire.core :as json]
   ))

;; Used to denote a Swagger 1.2 resource list
(defrecord ResourceListing [api-doc resources]
  bidi/Matched
  (resolve-handler [this m]
    (or
     (when (= (:remainder m) "api-docs")
       (merge
        (dissoc m :remainder)
        {:handler this
         ::type :resource-listing
         ::base-path (bidi/path-for (:route m) this)
         ::apis (into []
                      (for [[path api] (:resources this)]
                        (merge
                         (:api-doc api)
                         {:path (str (bidi/path-for (:route m) this)
                                     (:remainder m) "/"
                                     path)
                          })))}))
     (some identity
           (for [[path api] (:resources this)]
             (when (= (:remainder m) (str "api-docs/" path))
               (merge (dissoc m :remainder)
                      {::type :api-doc ; rename?
                       ::apis api
                       ::base-path (bidi/path-for (:route m) api)
                       :handler this}))))
     (resolve-handler (:resources this) (assoc m ::resource-listing this))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      ""
      (unresolve-handler (:resources this) m)))

  Handle
  (handle-request [this req match-context]
    {:status 200
     :body (json/encode
            (case (::type match-context)
              :resource-listing
              {:apiVersion (:api-version api-doc)
               :swaggerVersion "1.2"
               :basePath (::base-path match-context)
               #_:apis #_(::apis match-context)}
              :api-doc
              {:apiVersion (:api-version api-doc)
               :swaggerVersion "1.2"
               :greeting (pr-str match-context)}
              )
            {:pretty true}
            )}))

(defn swagger-paths [routes]
  (letfn [(encode-segment [segment]
            (cond
              (keyword? segment)
              (str "{" (name segment) "}")
              :otherwise segment))
          (encode [pattern]
            (cond (vector? pattern)
                  (apply str (map encode-segment pattern))
                  :otherwise pattern))
          (paths
            ([prefix route]
             (let [[pattern matched] route]
               (let [pattern (str prefix (encode pattern))]
                 (cond (vector? matched)
                       (apply concat
                              (for [route matched]
                                (paths pattern route)))
                       :otherwise [pattern matched]))))
            ([route]
             (map vec (partition 2 (paths nil route)))))]
    (into {} (paths routes))))

;; Used to denote a Swagger 1.2 resource (or resource grouping)
(defrecord Resource [api-doc route]
  bidi/Matched
  (resolve-handler [res m]
    ;; You can't resolve a Resource directly, only via a ResourceListing
    (resolve-handler (:route res) (assoc m ::resource res)))
  (unresolve-handler [res m]
    (when (= res (:handler m)) "")))

;; Used to denote end points
(defrecord Operation []
  bidi/Matched
  (resolve-handler [res m]
    (bidi/succeed res m))
  (unresolve-handler [res m]
    (when (= (:operationId res) (:handler m)) "")))
