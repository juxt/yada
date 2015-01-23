;; Copyright © 2014 JUXT LTD.

(ns yada.swagger-v1
  (:require
   [bidi.bidi :as bidi :refer (Matched resolve-handler unresolve-handler)]
   [bidi.ring :refer (Handle)]
   [hiccup.core :refer (html h)]
   [cheshire.core :as json]
   [clojure.walk :refer (postwalk)]
   ))

;; Even though ordering is technically irrelevant in the published api-docs JSON, it is important to human readers that it is readable, so we sort any how.

;; TODO: These sort map implementations are horrific, find proper replacements.

(defn key-order [& keys]
  (fn [x] (count (take-while (comp not (partial = x)) keys))))

(defn sort-map-inclusive
  "Include extra keys at the end"
  [m ks]
  (into (sorted-map-by (fn [x y] (apply < (map (apply key-order (distinct (concat ks (keys m)))) [x y]))))
        m))

(defn sort-map-exclusive
  "Return a map containing only the keys specified."
  [m ks]
  (into (apply sorted-map-by (fn [x y] (apply < (map (apply key-order ks) [x y]))) ks)
        (select-keys m ks)))

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
                      {::type :api-decl
                       ::apis (swagger-paths ["" (-> api :route)])
                       ;; ::base-path (bidi/path-for (:route m) api)
                       :handler this
                       }))))

     (resolve-handler (:resources this) (assoc m ::resource-listing this))))

  (unresolve-handler [this m]
    (if (= this (:handler m))
      ""
      (unresolve-handler (:resources this) m)))

  Handle
  (handle-request [this req match-context]
    {:status 200
     :body (json/encode
            (postwalk
             (fn [a] (if (:handler a) (dissoc a :handler) a))
             (case (::type match-context)

               :resource-listing
               (array-map
                :apiVersion (:api-version api-doc)
                :swaggerVersion "1.2"
                :basePath (::base-path match-context)
                :apis (map #(sort-map-exclusive % [:path :description]) (::apis match-context))
                :info (sort-map-inclusive
                       (:info api-doc) [:title :description
                                        :termsOfServiceUrl
                                        :contact
                                        :license :licenseUrl]))

               :api-decl
               (array-map
                :apiVersion (:api-version api-doc)
                :swaggerVersion "1.2"
                :apis (::apis match-context))
               ))
            {:pretty true}
            )}))

;; Used to denote a Swagger 1.2 resource (or resource grouping)
(defrecord Resource [api-doc route]
  bidi/Matched
  (resolve-handler [res m]
    ;; You can't resolve a Resource directly, only via a ResourceListing
    (resolve-handler (:route res) (assoc m ::resource res)))
  (unresolve-handler [res m]
    (when (= res (:handler m)) ""))
  )

;; Used to denote end points
(defrecord Operation []
  bidi/Matched
  (resolve-handler [res m]
    (bidi/succeed res m))
  (unresolve-handler [res m]
    (when (= (:operationId res) (:handler m)) ""))
  )
