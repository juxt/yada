(ns yada.mime
  (:refer-clojure :exclude [type])
  (:require [yada.util :refer (http-token Parameters parameters Weight weight)]))

;; For implementation efficiency, we often want to communicate media
;; types via records rather than encode in Strings which require
;; reparsing. This is the sole purpose of the MediaTypeMap record
;; below. In all cases, we can use strings interchangeably. For advanced
;; users only, who are concerned with performance.

(defprotocol MediaType
  "Content type, with parameters, as per rfc2616.html#section-3.7"
  (type [_] "")
  (subtype [_])
  (full-type [_] "type/subtype")
  (to-media-type-map [_] "Return an efficient version of this protocol"))

(defrecord MediaTypeMap [type subtype parameters weight]
  MediaType
  (type [_] type)
  (subtype [_] subtype)
  (full-type [_] (str type "/" subtype))
  (to-media-type-map [this] this)
  Parameters ; TODO: This is over use of protocols, use a map
  (parameters [_] parameters)
  (parameter [_ name] (get parameters name))
  Weight
  (weight [_] weight))

(def media-type
  (re-pattern (str "(" http-token ")"
                   "/"
                   "(" http-token ")"
                   "((?:" ";" http-token "=" http-token ")*)")))

(memoize
 (defn string->media-type [s]
   (let [g (rest (re-matches media-type s))
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

(extend-protocol MediaType
  String
  (to-media-type-map [s] (string->media-type s)))

(defmethod clojure.core/print-method MediaTypeMap
  [mt ^java.io.Writer writer]
  (.write writer (format "%s/%s%s%s"
                         (type mt)
                         (subtype mt)
                         (when-let [w (weight mt)] (str ";q=" w))
                         (apply str (for [[k v] (parameters mt)]
                                      (format ";%s=%s" k v))))))
