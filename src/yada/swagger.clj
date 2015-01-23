(ns yada.swagger
  (:require
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler succeed match-pair)]
   [bidi.ring :refer (Handle)]
   [cheshire.core :as json]
   [cheshire.generate :refer (JSONable write-string)]
   [camel-snake-kebab :as csk])
  (:import (clojure.lang Keyword)))

(defrecord Op [op-defn]
  Matched
  (resolve-handler [this m] (succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) ""))
  Handle
  (handle-request [_ req match-context]
    {:status 200 :body "Op! TODO"}))

#_(extend-protocol JSONable
  Keyword
  (to-json [t jg]
    (write-string ^JsonGenerator jg "XXX"))
  )


(defn op [op-defn]
  (->Op op-defn))

(defrecord Swagger [spec]
  Matched
  (resolve-handler [this m]
    (if (= (:remainder m) "/swagger.json")
      (merge (dissoc m :remainder) {:handler this})
      (resolve-handler (:paths spec) m)))
  (unresolve-handler [_ m]
    (throw (ex-info "TODO" {})))

  Handle
  (handle-request [_ req match-context]
    {:status 200
     :headers {"content-type" "application/json"}
     :body (json/encode spec {:pretty true
                              :key-fn (fn [x] (csk/->camelCase (name x)))})}
    ))

(defn swagger [spec]
  [(or (:base-path spec) "/")
   (->Swagger spec)])
