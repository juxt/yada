;; Copyright © 2015, JUXT LTD.

;; This possibly belongs in another library. It depends on bidi, but is
;; independent of the yada handler. Break swag out of yada, leaving yada
;; as an 'async liberator'. It should be easy to use the swagger parts,
;; but plug-in a custom handler. It should also be useful to use yada
;; without swagger.

(ns yada.swagger
  (:require
   [clojure.set :as set]
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler succeed match-pair unmatch-pair)]
   [bidi.ring :refer (Handle)]
   [cheshire.core :as json]
   [cheshire.generate :refer (JSONable write-string)]
   [camel-snake-kebab :as csk]
   [yada.core :refer (make-async-handler)]
   [clojure.walk :refer (postwalk)])
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
                       :otherwise [pattern matched])))))]
    (into {} (mapcat #(map vec (partition 2 (paths "" %))) routes))))

(defprotocol Handler
  (handle-api-request [_ req spec path-item op] "Handle an API request"))

(extend-protocol Handler
  nil
  (handle-api-request [_ req spec path-item op]
    {:status 500
     :body (str "No handler specified to handle op: " (pr-str op))}))

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
    (handle-api-request
     (::handler match-context)
     req
     (::swagger-spec match-context)
     this
     (get this (:request-method req)))))

(defn prune-namespaced-keywords
  "Deep remove of namespaced keywords from a map. This is so that extra
  entries can be added to an API structure, which can be pruned prior to
  publication as a Swagger JSON spec."
  [m]
  (postwalk
   (fn [a]
     (if (map? a)
       (reduce-kv (fn [acc k v]
                    (if (and (keyword? k) (namespace k))
                      acc
                      (assoc acc k v)))
                  {} a)
       a))
   m))

(defrecord Swagger [spec handler]
  Matched
  (resolve-handler [this m]
    (if (= (:remainder m) (str (or (:base-path spec) "") "/swagger.json"))
      (-> m
          (assoc :handler this)
          (dissoc :remainder))
      (resolve-handler [[(or (:base-path spec) "") (:paths spec)]]
                       (merge m
                              {::swagger-spec spec}
                              (when handler {::handler handler})))))
  (unresolve-handler [this m]
    (if (= this (:handler m))
      (or (:base-path spec) "")
      (unresolve-handler (:paths spec) m)))

  Handle
  (handle-request [_ req match-context]
    {:status 200
     :headers {"content-type" "application/json"}
     :body (-> spec
             (update-in [:paths] swagger-paths)
             prune-namespaced-keywords
             (json/encode
              {:pretty true
               :key-fn (fn [x] (csk/->camelCase (name x)))}))}))

(defn swagger
  ([spec handler]
   (->Swagger (-> spec
                  ((partial merge {:swagger "2.0"}))
                  (update-in [:info] (partial merge {:title "Untitled"
                                                     :version "0.0.1"})))
              handler))
  ([spec]
   (swagger spec nil)))


;; A default async handler that adapts yada.core to this ns

(defrecord DefaultAsyncHandler []
  Handler
  (handle-api-request [_ req spec path-item op]
    ;; If op is nil we could respond with a 405 (Method Not Allowed) but
    ;; we opt for letting the yada handler do this because it might want
    ;; to handle this situation differently. There might be other
    ;; responses, (e.g. 503, 403), that override the 405 (e.g.  if
    ;; revealing a 405 leaks information about the API to an untrusted
    ;; party).
    ((make-async-handler
      (merge
       {:allowed-method?
        (set/intersection #{:get :put :post :delete :options :head :patch}
                          (set (keys path-item)))
        :produces (or (:produces op)
                      (when-let [body (-> op :yada/handler :body)]
                        (when (map? body) (set (keys body))))
                      (:produces spec))}
       (:yada/opts spec)
       (:yada/handler op)))
     req)))
