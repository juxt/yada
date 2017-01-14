;; Copyright © 2014-2017, JUXT LTD.

(ns yada.charset
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.xml :as xml]
   [yada.util :refer :all]))

;; TODO: Replace with java.nio.charset.Charset, which contains the same logic

(def charsets-xml-doc
  (xml/parse
   (io/input-stream (io/resource "yada/Character Sets.xml"))))

(def records (comp (tag= :registry) (tag= :record)))

(def index
  (for [rec (sequence (comp records) [charsets-xml-doc])]
    {:name (first (sequence (comp (tag= :name) text) [rec]))
     :preferred-alias (first (sequence (comp (tag= :preferred_alias) text) [rec]))
     :aliases (sequence (comp (tagp (partial #{:alias :preferred_alias})) text) [rec])}))

(def alias->name
  (into {} (for [{:keys [name aliases]} index alias (conj aliases name)] [(.toUpperCase alias) name])))

(defn valid-charset? [charset] (contains? alias->name (.toUpperCase charset)))

(def name->alias
  (into {} (for [{:keys [name preferred-alias]} index] [name (or preferred-alias name)])))

(defprotocol Charset
  "Charset, with parameters, as per rfc2616.html#section-5.3.3"
  (charset [_] "")
  (canonical-name [_] "")
  (preferred-alias [_]))

(defrecord CharsetMap [alias quality]
  Charset
  (charset [_] alias)
  (canonical-name [_] (get alias->name (.toUpperCase alias)))
  (preferred-alias [this] (name->alias (canonical-name this)))
  Comparable
  (compareTo [this other] (. (java.text.Collator/getInstance) compare (:alias this) (:alias other))))

(def charset-pattern
  (re-pattern (str "(" http-token ")"
                   "((?:" ";" http-token "=" http-token ")*)")))

;; TODO: Support * - see rfc7231: The special value "*", if present in the
;; Accept-Charset field, matches every charset that is not mentioned
;; elsewhere in the Accept-Charset field.  If no "*" is present in an
;; Accept-Charset field, then any charsets not explicitly mentioned in
;; the field are considered "not acceptable" to the client.

(def string->charset*
  (memoize
   (fn [s]
     (let [g (rest (re-matches charset-pattern s))]
       (when (last g)
         (let [params (into {} (map vec (map rest (re-seq (re-pattern (str ";(" http-token ")=(" http-token ")"))
                                                          (last g)))))]
           (->CharsetMap
            (first g)
            (if-let [q (get params "q")]
              (try
                (Float/parseFloat q)
                (catch java.lang.NumberFormatException e
                  (float 1.0)))
              (float 1.0)))))))))

(defn- string->charsetmap [s]
  (string->charset* (str/trim s)))

(defprotocol Coercions
  (to-charset-map [_]))

(extend-protocol Coercions
  CharsetMap
  (to-charset-map [c] c)
  String
  (to-charset-map [s]
    (string->charsetmap s)))

(def utf-8-charset (.name (java.nio.charset.StandardCharsets/UTF_8)))
(def default-platform-charset (.name (java.nio.charset.Charset/defaultCharset)))

(def platform-charsets
  (let [preferred-charsets (->> [utf-8-charset default-platform-charset]
                                distinct
                                (map to-charset-map)
                                (map #(assoc %2 :quality %1) [(float 1.0) 0.9]))
        other-supported-charsets (->> (java.nio.charset.Charset/availableCharsets)
                                      keys
                                      (filter (comp not (->> preferred-charsets (map :alias) set)))
                                      (map to-charset-map)
                                      (map #(assoc % :quality 0.8))
                                      ;; Tune down the number of charsets to manageable level by
                                      ;; excluding those prefixed by x- and 'IBM'.
                                      (filter (comp not (partial re-matches #"(x-|IBM).*") :alias))
                                      )]
    (set (concat
           preferred-charsets
           other-supported-charsets))))
