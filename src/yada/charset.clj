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

(defn valid-charset? [charset] (contains? alias->name (.toUpperCase charset)))

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
  (to-charset-map [this] this))

(def charset-pattern
  (re-pattern (str "(" http-token ")"
                   "((?:" ";" http-token "=" http-token ")*)")))

(memoize
 (defn string->charset [s]
   (let [g (rest (re-matches charset-pattern s))
         params (into {} (map vec (map rest (re-seq (re-pattern (str ";(" http-token ")=(" http-token ")"))
                                                    (last g)))))]
     (->CharsetMap
      (first g)
      (if-let [q (get params "q")]
        (try
          (Float/parseFloat q)
          (catch java.lang.NumberFormatException e
            1.0))
        1.0)))))

(extend-protocol Charset
  String
  (to-charset-map [s] (string->charset s)))

(defmethod clojure.core/print-method CharsetMap
  [cs ^java.io.Writer writer]
  (.write writer (format "%s%s%s"
                         (preferred-alias cs)
                         (when-let [w (:weight cs)] (str ";q=" w)))))
