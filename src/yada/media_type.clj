;; Copyright © 2015, JUXT LTD.

(ns yada.media-type
  (:refer-clojure :exclude [type])
  (:require
   [yada.util :refer (http-token OWS)]
   [clojure.tools.logging :refer :all]))

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
                   "((?:" OWS ";" OWS http-token "=" http-token ")*)")))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(memoize
 (defn string->media-type [s]
   (when s
     (let [g (rest (re-matches media-type-pattern s))
           params (into {} (map vec (map rest (re-seq (re-pattern (str ";" OWS "(" http-token ")=(" http-token ")"))
                                                      (last g)))))]
       (->MediaTypeMap
        (str (first g) "/" (second g))
        (first g)
        (second g)
        (dissoc params "q")
        (if-let [q (get params "q")]
          (try
            (Float/parseFloat q)
            (catch java.lang.NumberFormatException e
              (float 1.0)))
          (float 1.0)))))))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(memoize
 (defn media-type->string [mt]
   (when mt
     (assert (instance? MediaTypeMap mt))
     (.toLowerCase
      (str (:name mt)
           (apply str (for [[k v] (:parameters mt)]
                        (str ";" k "=" v))))))))


