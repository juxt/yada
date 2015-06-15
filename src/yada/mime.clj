(ns yada.mime
  (:refer-clojure :exclude [type]))

;; For implementation efficiency, we often want to communicate media
;; types via records rather than encode in Strings which require
;; reparsing. This is the sole purpose of the MediaTypeMap record
;; below. In all cases, we can use strings interchangeably. For advanced
;; users only, who are concerned with performance.

(defprotocol MediaType
  "Content type, with parameters, as per rfc2616.html#section-3.7"
  (type [_] "")
  (subtype [_])
  (parameter [_ name])
  (full-type [_] "type/subtype")
  (to-media-type-map [_] "Return an efficient version of this protocol"))

(defrecord MediaTypeMap [type subtype parameters]
  MediaType
  (type [_] type)
  (subtype [_] subtype)
  (full-type [_] (str type "/" subtype))
  (parameter [_ name] (get parameters name))
  (to-media-type-map [this] this))


(def token #"[^()<>@,;:\\\"/\[\]?={}\ \t]+")

(def media-type
  (re-pattern (str "(" token ")"
                   "/"
                   "(" token ")"
                   "((?:" ";" token "=" token ")*)")))

(memoize
 (defn string->media-type [s]
   (let [g (rest (re-matches media-type s))]
     (->MediaTypeMap
      (first g)
      (second g)
      (into {} (map vec (map rest (re-seq (re-pattern (str ";(" token ")=(" token ")"))
                                          (last g)))))))))


(extend-protocol MediaType
  String
  (to-media-type-map [s] (string->media-type s)))
