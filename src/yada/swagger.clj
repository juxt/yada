(ns yada.swagger
  (:require
   [bidi.bidi :refer (Matched resolve-handler unresolve-handler succeed match-pair)]
   [bidi.ring :refer (Handle)])
  )

(defrecord Op [op-defn]
  Matched
  (resolve-handler [this m] (succeed this m))
  (unresolve-handler [this m] (when (= this (:handler m)) ""))
  Handle
  (handle-request [_ req match-context]
    {:status 200 :body "Op! TODO"}))

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
    {:status 200 :body "Swagger! TODO"}
    ))

(defn swagger [spec]
  [(or (:base-path spec) "/")
   (->Swagger spec)])
