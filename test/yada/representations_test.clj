;; Copyright © 2015, JUXT LTD.

(ns yada.representations-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.util :refer (parse-csv)]
   [yada.negotiation :as negotiation]))

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

(deftest best-test
  (is (= (best [3 2 3 nil 19]) 19))
  (is (= (best (comp - compare) [3 2 3 nil 19]) nil)))

(deftest best-by-test
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [nil 0] [19 10]]) [nil 0]))
  (is (= (best-by first (comp - compare) [[3 9] [2 20] [3 -2] [-2 0] [19 10]]) [-2 0])))

;; These are higher-order wrappers used by all dimensios of proactive
;; negotiation.

(defn- skip-rejected
  "Short-circuit attempts to process already rejected representation
  metadata."
  [f]
  (fn [rep]
    (if (:rejected rep) rep (f rep))))

(defn- wrap-quality-assessor
  "Return a function that will either reject, or associate a quality, to
  the given representation metadata."
  [f k]
  (fn [rep]
    (if-let [quality (f rep)]
      (assoc-in rep [:qualities k] quality)
      (assoc rep :rejected k))))

;; Content type negotation

(defn content-type-acceptable?
  "Compare a single acceptable mime-type (extracted from an Accept
  header) and a candidate. If the candidate is acceptable, return a
  sortable vector [acceptable-quality specificity parameter-count
  candidate-quality]. Specificity prefers text/html over text/* over
  */*. Parameter count gives preference to candidates with a greater
  number of parameters, which prefers text/html;level=1 over
  text/html. This meets the criteria in the HTTP
  specifications. Although the preference that should result with
  multiple parameters is not specified formally, candidates that have a
  greater number of parameters are preferred."
  ;; It is possible that these qualities could be coded into a long, since
  ;; "A sender of qvalue MUST NOT generate more than three digits after
  ;; the decimal point.  User configuration of these values ought to be
  ;; limited in the same fashion." -- RFC 7231 Section 5.3.1
  [rep acceptable]
  (when
      (= (:parameters acceptable) (:parameters rep))
    (cond
      (and (= (:type acceptable) (:type rep))
           (= (:subtype acceptable) (:subtype rep)))
      [(:quality acceptable) 3 (count (:parameters rep)) (:quality rep)]

      (and (= (:type acceptable) (:type rep))
           (= (:subtype acceptable) "*"))
      [(:quality acceptable) 2 (count (:parameters rep)) (:quality rep)]

      (and (= (mime/media-type acceptable) "*/*"))
      [(:quality acceptable) 1 (count (:parameters rep)) (:quality rep)])))

(defn best-content-type-acceptable?
  "Given a collection of acceptable mime-types, return a function that will return the quality."
  [accepts]
  (fn [rep]
    (best (map (partial content-type-acceptable? (:content-type rep)) accepts))))

(defn make-content-type-quality-assessor
  [req k]
  (->
   (->> (get-in req [:headers "accept"]) parse-csv (map mime/string->media-type))
   best-content-type-acceptable?
   (wrap-quality-assessor :content-type)
   skip-rejected))

(defn get-content-type-quality
  "Given the request and a representation, get the quality of the representation."
  [req rep]
  (let [k :content-type
        f (make-content-type-quality-assessor req k)
        rep (f rep)]
    (or (get-in rep [:qualities k])
        (when (:rejected rep) :rejected))))

(deftest content-type-test
  ;; Basic match
  (is (= (get-content-type-quality
          {:headers {"accept" "text/html"}}
          {:content-type (mime/string->media-type "text/html")})
         [(float 1.0) 3 0 (float 1.0)]))

  ;; Basic match, with multiple options
  (is (= (get-content-type-quality
          {:headers {"accept" "image/png,text/html"}}
          {:content-type (mime/string->media-type "text/html")})
         [(float 1.0) 3 0 (float 1.0)]))

  ;; Basic match, with multiple options and q values
  (is (= (get-content-type-quality
          {:headers {"accept" "image/png,text/html;q=0.9"}}
          {:content-type (mime/string->media-type "text/html;q=0.8")})
         [(float 0.9) 3 0 (float 0.8)]))

  ;; Basic reject
  (is (= (get-content-type-quality
          {:headers {"accept" "text/html"}}
          {:content-type (mime/string->media-type "text/plain")})
         :rejected))

  ;; Basic reject with multiple options
  (is (= (get-content-type-quality
          {:headers {"accept" "image/png,text/html"}}
          {:content-type (mime/string->media-type "text/plain")})
         :rejected))

  ;; Wildcard match
  (is (= ((get-content-type-quality
           {:headers {"accept" "image/png,text/*"}}
           {:content-type (mime/string->media-type "text/html")}) 1)
         ;; We get a match with a specificty score of 2
         2))

  ;; Specific match beats wildcard
  (is (= ((get-content-type-quality
           {:headers {"accept" "image/png,text/*,text/html"}}
           {:content-type (mime/string->media-type "text/html")}) 1)
         ;; We get a specificty score of 3, indicating we matched on the
         ;; text/html rather than the preceeding text/*
         3))

  ;; Specific match beats wildcard, different order
  (is (= ((get-content-type-quality
           {:headers {"accept" "text/html,text/*,image/png"}}
           {:content-type (mime/string->media-type "text/html")}) 1)
         3))

  ;; Greater number of parameters matches
  (is (= ((get-content-type-quality
           {:headers {"accept" "text/html,text/html;level=1"}}
           {:content-type (mime/string->media-type "text/html;level=1")}) 2)
         ;; We get a specificty score of 3, indicating we matched on the
         ;; text/html rather than the preceeding text/*
         1))

  ;; TODO: Test content type parameters

  ;; TODO: Test charsets

  ;; TODO: Test encodings

  )

;; "A request without any Accept header
;; field implies that the user agent
;; will accept any media type in response."
;; -- RFC 7231 Section 5.3.2

;; "A request without any Accept-Charset header field implies
;; that the user agent will accept any charset in response. "
;; -- RFC 7231 Section 5.3.3
