;; Copyright © 2014-2016, JUXT LTD.

;; Depends on Swagger

(ns yada.json
  (:require
   [byte-streams :as bs]
   [yada.request-body :refer [parse-stream default-matcher process-request-body with-400-maybe default-process-request-body]]
   [ring.swagger.coerce :as rsc]
   [yada.body :refer [render-map render-seq]]
   [cheshire.generate :refer [add-encoder encode-map]]
   [cheshire.core :as json]))

;; Outbound

(defmethod render-map "application/json"
  [m representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (str (json/encode m {:pretty pretty}) \newline)))

(defmethod render-seq "application/json"
  [s representation]
  (let [pretty (get-in representation [:media-type :parameters "pretty"])]
    (str (json/encode s {:pretty pretty}) \newline)))

(add-encoder
 java.lang.Exception
 (fn [ei jg]
   (encode-map
    (merge
     {:error (str ei)}
     (when (instance? clojure.lang.ExceptionInfo ei)
       {:data (pr-str (ex-data ei))}))
    jg)))


;; Inbound

(defmethod parse-stream "application/json"
  [_ stream]
  (-> (bs/to-string stream)
      (json/decode keyword)
      (with-400-maybe)))

(defmethod default-matcher "application/json" [_]
  (rsc/coercer :json))

(defmethod process-request-body "application/json"
  [& args]
  (apply default-process-request-body args))
