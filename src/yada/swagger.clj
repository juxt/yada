;; Copyright © 2015, JUXT LTD.

;; This possibly belongs in another library. It depends on bidi, but is
;; independent of the yada handler. Break swag out of yada, leaving yada
;; as an 'async liberator'. It should be easy to use the swagger parts,
;; but plug-in a custom handler. It should also be useful to use yada
;; without swagger.

(ns yada.swagger
  (:require
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler succeed match-pair unmatch-pair)]
   [bidi.ring :refer (Handle)]
   [cheshire.core :as json]
   [cheshire.generate :refer (JSONable write-string)]
   [camel-snake-kebab :as csk])
  (:import (clojure.lang Keyword)))

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
            )]
    (into {} (mapcat #(map vec (partition 2 (paths "" %))) routes))))

;; We override bidi's default map implementation, thereby replacing
;; bidi's request-method guards with one that interprets various Swagger
;; objects.
(extend-type clojure.lang.APersistentMap
  Matched
  (resolve-handler [this m]
    (if (::swagger-spec m)
      (succeed this m)
      (some #(match-pair % m) this)))
  (unresolve-handler [this m]
    (some #(unmatch-pair % m) this))
  Handle
  (handle-request [this req match-context]
    {:status 200
     :body (pr-str this)}))

(defrecord Swagger [spec]
  Matched
  (resolve-handler [this m]
    (if (= (:remainder m) "/swagger.json")
      (merge (dissoc m :remainder) {:handler this})
      (resolve-handler (:paths spec) (assoc m ::swagger-spec spec))))
  (unresolve-handler [_ m]
    (throw (ex-info "TODO" {})))

  Handle
  (handle-request [_ req match-context]
    {:status 200
     :headers {"content-type" "application/json"}
     :body (-> spec
             (update-in [:paths] swagger-paths)
             (json/encode {:pretty true
                           :key-fn (fn [x] (csk/->camelCase (name x)))}))}
    ))

(defn swagger [spec]
  [(or (:base-path spec) "/")
   (->Swagger (-> spec
                ((partial merge {:swagger "2.0"}))
                (update-in [:info] (partial merge {:title "Untitled"
                                                   :version "0.0.1"}))

                ))])
