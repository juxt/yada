;; Copyright © 2014-2017, JUXT LTD.

(ns yada.media-type
  (:refer-clojure :exclude [type])
  (:require
   [clojure.tools.logging :refer :all]
   [yada.util :refer [http-token OWS]]))

;; For implementation efficiency, we keep the parsed versions of media
;; types as records rather than encode in Strings which require
;; reparsing. This is the sole purpose of the MediaTypeMap record below.

(defrecord MediaTypeMap [name type subtype parameters quality])

(defn ^:deprecated media-type
  "DEPRECATED: Use :name on the given media type"
  [mt]
  (warnf "Use of media-type function is deprecated")
  (Thread/dumpStack)
  (when (:type mt) (str (:type mt) "/" (:subtype mt))))

(def media-type-pattern
  (re-pattern (str "(" http-token ")"
                   "/"
                   "(" http-token ")"
                   "((?:" OWS ";" OWS http-token "=(?:(?:" http-token ")|\"(?:" http-token ")\"))*)")))

(def media-type-pattern-no-subtype
  (re-pattern (str "(\\*)"
                   "((?:" OWS ";" OWS http-token "=(?:(?:" http-token ")|\"(?:" http-token ")\"))*)")))

(def parameter-pattern
  (re-pattern (str ";" OWS "(" http-token ")=(?:(" http-token ")|\"(" http-token ")\")")))

(defn- match-media-type
  [s]
  (next (re-matches media-type-pattern s)))

(defn- match-media-type-no-subtype
  [s]
  (when-let [match (re-matches media-type-pattern-no-subtype s)]
    (next (concat (take 2 match)
                  ["*" (last match)]))))

(defn- parse-media-type-parameters
  [parameters]
  (->> (re-seq parameter-pattern parameters)
       (map rest)
       (map (partial filter some?))
       (map vec)
       (into {})))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(def string->media-type
  (memoize
   (fn [s]
     (when s
       (when-let [[type subtype :as media-type-parts] (or (match-media-type s)
                                                          (match-media-type-no-subtype s))]
         (let [parameters (parse-media-type-parameters (last media-type-parts))]
           (->MediaTypeMap
            (str type "/" subtype)
            type
            subtype
            (dissoc parameters "q")
            (if-let [q (get parameters "q")]
              (try
                (Float/parseFloat q)
                (catch java.lang.NumberFormatException e
                  (float 1.0)))
              (float 1.0)))))))))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(def media-type->string
  (memoize
   (fn [mt]
     (when mt
       (assert (instance? MediaTypeMap mt))
       (.toLowerCase
        (str (:name mt)
             (apply str (for [[k v] (:parameters mt)]
                          (str ";" k "=" v)))))))))
