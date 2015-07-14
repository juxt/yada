(ns yada.mime
  (:refer-clojure :exclude [type])
  (:require [yada.util :refer (http-token)]))

;; For implementation efficiency, we keep the parsed versions of media
;; types as records rather than encode in Strings which require
;; reparsing. This is the sole purpose of the MediaTypeMap record below.

(defrecord MediaTypeMap [type subtype parameters weight])

(defn media-type [mt] (str (:type mt) "/" (:subtype mt)))

(def media-type-pattern
  (re-pattern (str "(" http-token ")"
                   "/"
                   "(" http-token ")"
                   "((?:" ";" http-token "=" http-token ")*)")))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(memoize
 (defn string->media-type [s]
   (let [g (rest (re-matches media-type-pattern s))
         params (into {} (map vec (map rest (re-seq (re-pattern (str ";(" http-token ")=(" http-token ")"))
                                                    (last g)))))]
     (->MediaTypeMap
      (first g)
      (second g)
      (dissoc params "q")
      (if-let [q (get params "q")]
        (try
          (Float/parseFloat q)
          (catch java.lang.NumberFormatException e
            1.0))
        1.0)))))

;; TODO: Replace memoize with cache to avoid memory exhaustion attacks
(memoize
 (defn media-type->string [mt]
   (assert (instance? MediaTypeMap mt))
   (.toLowerCase
    (str (media-type mt)
         (apply str (for [[k v] (:parameters mt)]
                      (str ";" k "=" v)))))))

(defmethod clojure.core/print-method MediaTypeMap
  [mt ^java.io.Writer writer]
  (.write writer (format "#yada.media-type[%s%s%s]"
                         (media-type mt)
                         (when-let [w (:weight mt)] (str ";q=" w))
                         (apply str (for [[k v] (:parameters mt)]
                                      (format ";%s=%s" k v))))))
