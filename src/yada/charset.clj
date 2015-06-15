(ns yada.charset
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [yada.util :refer :all]))

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

(def name->alias
  (into {} (for [{:keys [name preferred-alias]} index] [name (or preferred-alias name)])))

(defprotocol Charset
  "Charset, with parameters, as per rfc2616.html#section-5.3.3"
  (charset [_] "")
  (canonical-name [_] "")
  (preferred-alias [_])
  (to-charset-map [_] "Return an efficient version of this protocol"))

(defrecord CharsetMap [alias weight]
  Charset
  (charset [_] alias)
  (canonical-name [_] (get alias->name (.toUpperCase alias)))
  (preferred-alias [this] (name->alias (canonical-name this)))
  (to-charset-map [this] this)
  Weight
  (weight [_] weight))

(def charset
  (re-pattern (str "(" http-token ")"
                   "((?:" ";" http-token "=" http-token ")*)")))

(memoize
 (defn string->charset [s]
   (let [g (rest (re-matches charset s))
         params (into {} (map vec (map rest (re-seq (re-pattern (str ";(" http-token ")=(" http-token ")"))
                                                    (last g)))))]
     (->CharsetMap
      (first g)
      (try
        (Float/parseFloat (get params "q"))
        (catch java.lang.NumberFormatException e
          1.0))))))

(extend-protocol Charset
  String
  (to-charset-map [s] (string->charset s)))

(defmethod clojure.core/print-method CharsetMap
  [cs ^java.io.Writer writer]
  (.write writer (format "%s%s%s"
                         (preferred-alias cs)
                         (apply str (for [[k v] (parameters cs)
                                          :when (not= k "q")]
                                      (format ";%s=%s" k v)))
                         (when-let [w (weight cs)] (str ";q=" w)))))
