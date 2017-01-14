;; Copyright © 2014-2017, JUXT LTD.

(ns yada.util
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str])
  (:import clojure.lang.IPersistentVector
           java.util.Map java.util.Map$Entry))

;; ------------------------------------------------------------------------
;; XML Parsing Transducers

(def children (mapcat :content))

(defn tagp [pred]
  (comp children (filter (comp pred :tag))))

(defn tag= [tag]
  (tagp (partial = tag)))

(defn attr-accessor [a]
  (comp a :attrs))

(defn attrp [a pred]
  (filter (comp pred (attr-accessor a))))

(defn attr= [a v]
  (attrp a (partial = v)))

(def text (comp (mapcat :content) (filter string?)))

;; Parsing

(def CRLF "\r\n")
(def OWS #"[ \t]*")
(def http-token #"[!#$%&'*+-\.\^_`|~\p{Alnum}]+")

;; ETags

(defn md5-hash [^String s]
  (let [digest
        (doto
            (java.security.MessageDigest/getInstance "MD5")
          (.update (.getBytes s) 0 (.length s)))]
    (format "%1$032x" (java.math.BigInteger. 1 (.digest digest)))))

;; CSV

(defn parse-csv [v]
  (when v
    (map str/trim (str/split v #"\s*,\s*"))))


;; Selection

(defn best
  "Pick the best item from a collection. Since we are only interested in
  the best, we can avoid sorting the entire collection, which could be
  inefficient with large collections. The best element is selected by
  comparing items. An optional comparator can be given."
  ([coll]
   (best compare coll))
  ([^java.util.Comparator comp coll]
   (reduce
    (fn [x y]
      (case (. comp (compare x y)) (0 1) x -1 y))
    coll)))

(defn best-by
  "Pick the best item from a collection. Since we are only interested in
  the best, we can avoid sorting the entire collection, which could be
  inefficient with large collections. The best element is selected by
  applying the function given by keyfn to each item and comparing the
  result. An optional comparator can be given. The implementation uses a
  pair to keep hold of the result of applying the keyfn function, to
  avoid the redundancy of calling keyfn unnecessarily."
  ([keyfn coll]
   (best-by keyfn compare coll))
  ([keyfn ^java.util.Comparator comp coll]
   (first ;; of the pair
    (reduce (fn [x y]
              (if-not x
                ;; Our first pair
                [y (keyfn y)]
                ;; Otherwise compare
                (let [py (keyfn y)]
                  (case (. comp (compare (second x) py))
                    (0 1) x
                    -1 [y py]))))
            nil ;; seed
            coll))))


;; URLs

(defn as-file [^java.net.URL resource]
  (when resource
    (case (.getProtocol resource)
      "file" (io/file (.getFile resource))
      "jar" (io/file (.getFile (java.net.URL. (first (str/split (.getFile resource) #"!")))))
      nil)))


;; Useful functions

(defn remove-empty-vals [m]
  (reduce-kv (fn [acc k v] (if (not-empty v) (assoc acc k v) acc)) {} m))


;; Parameters

(defn merge-parameters
  "Merge parameters such that method parameters override resource
  parameters, and that parameter schemas (except for the single body
  parameter) are combined with merge."
  [resource-params method-params]
  (merge
   (apply merge-with merge (map #(dissoc % :body) [resource-params method-params]))
   (select-keys resource-params [:body])
   (select-keys method-params [:body])))

;; Request helpers

(defn get-host-origin [req]
  (str (name (:scheme req)) "://"  (get-in req [:headers "host"])))

(defn same-origin? [req]
  (let [host (get-host-origin req)
        origin (get-in req [:headers "origin"])]
    (= host origin)))

;; Get from maps that allow for key 'sets' and wildcards

(defn get*
  "Like get, but keys can be sets and the wildcard '*'"
  [m k]
  (or (get m k)
      (some (fn [[k* v]]
              (when (and (set? k*) (contains? k* k)) v))
            m)
      (get m *)))

(defn disjoint*?
  "Checks that map keys are disjoint. Meaning a given key matches only one mapping in the map."
  [m]
  (let [direct (set (filter #(not (set? %)) (keys (dissoc m *))))
        sets (cons direct (filter set? (keys m)))]
    (= (reduce + (map count sets))
       (count (apply set/union sets)))))

(defn- replace-set-key [map old-key new-key val]
  (let [dis (dissoc map old-key)]
    (cond
      (= (count new-key) 0) dis
      (= (count new-key) 1) (assoc dis (first new-key) val)
      :else (assoc dis new-key val))))

(defn dissoc*
  "Like dissoc but keys can be sets and the wildcard '*' and it will
  ensure that the returned map does not contain a mapping for key when
  calling get* with the exception of the wildcard.

  A few examples:
  (dissoc* {200 :a #{400 401} :b * :c} 200) => {#{400 401} :b * :c}
  (dissoc* {200 :a #{400 401} :b * :c} 400) => {200 :a 401 :b * :c}
  (dissoc* {200 :a #{400 401} :b * :c} #{200 401}) => {400 :b * :c}"
  ([map key]
   (reduce-kv (fn [ret k val]
                (cond
                  (= key k) (dissoc ret k)
                  (and (set? k) (set? key)) (let [d (set/difference k key)]
                                              (if (< (count d) (count k))
                                                (replace-set-key ret k d val)
                                                ret))
                  (and (set? key) (contains? key k)) (dissoc ret k)
                  (and (set? k) (contains? k key)) (replace-set-key ret k (disj k key) val)
                  :else ret))
              map map))
  ([map key & ks]
   (let [ret (dissoc* map key)]
     (if ks
       (recur ret (first ks) (next ks))
       ret))))

(defn assoc*
  "Like assoc but the keys can be sets and the wildcard '*' "
  ([map key val]
   (assoc (dissoc* map key) key val))
  ([map key val & kvs]
   (let [ret (assoc* map key val)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                  "assoc* expects even number of arguments after map/vector, found odd number")))
       ret))))

(defn conj*
  ([map] map)
  ([map o]
   (cond
     (instance? Map$Entry o) (let [^Map$Entry pair o]
                               (assoc* map (.getKey pair) (.getValue pair)))
     (instance? IPersistentVector o) (let [^IPersistentVector vec o]
                                       (assoc* map (.nth vec 0) (.nth vec 1)))
     :else (loop [map map
                  o o]
             (if (seq o)
               (let [^Map$Entry pair (first o)]
                 (recur (assoc* map (.getKey pair) (.getValue pair)) (rest o)))
               map))))
  ([map x & xs]
   (if xs
     (recur (conj* map x) (first xs) (next xs))
     (conj* map x))))

(defn merge*
  "Returns a map that consists of the rest of the maps conj-ed onto
  the first using conj*.  If a key occurs in more than one map, the mapping from
  the latter (left-to-right) will be the mapping in the result."
  [& maps]
  (when (some identity maps)
    (reduce #(conj* (or %1 {}) %2) maps)))

(merge)

(defn expand
  "Expands the set keys and returns a map
  (expand {#{200 300} :test}) => {200 :test 300 :test}"
  [map]
  (-> (reduce-kv (fn [h k v]
                   (if (set? k)
                     (reduce #(assoc! %1 %2 v) h k)
                     (assoc! h k v)))
                 (transient {}) map)
      persistent!))

;; Arity

(defn arity [f]
  (let [^java.lang.reflect.Method m (first (.getDeclaredMethods (class f)))
        p (.getParameterTypes m)]
    (alength p)))
