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

(defn content-type-acceptable?
  "Compare a single acceptable mime-type (extracted from an Accept
  header) and a candidate. If the candidate is acceptable, return a
  sortable vector [acceptable-weight specificity parameter-count
  candidate-weight]. Specificity prefers text/html over text/* over
  */*. Parameter count gives preference to candidates with a greater
  number of parameters, which prefers text/html;level=1 over
  text/html. This meets the criteria in the HTTP
  specifications. Although the preference that should result with
  multiple parameters is not specified formally, candidates that have a
  greater number of parameters are preferred."
  [candidate acceptable]
  (when
      (= (:parameters acceptable) (:parameters candidate))
    (cond
      (and (= (:type acceptable) (:type candidate))
           (= (:subtype acceptable) (:subtype candidate)))
      [(:weight acceptable) 3 (count (:parameters candidate)) (:weight candidate)]

      (and (= (:type acceptable) (:type candidate))
           (= (:subtype acceptable) "*"))
      [(:weight acceptable) 2 (count (:parameters candidate)) (:weight candidate)]

      (and (= (mime/media-type acceptable) "*/*"))
      [(:weight acceptable) 1 (count (:parameters candidate)) (:weight candidate)])))

(defn best-content-type-acceptable? [accepts]
  (fn [rep]
    (best-by identity (map (partial content-type-acceptable? (:content-type rep)) accepts))))

(defn skip-rejected [f]
  (fn [rep]
    (if (:rejected rep) rep (f rep))))

(defn wrap-weigher [f k]
  (fn [rep]
    (if-let [weight (f rep)]
      (assoc-in rep [:weights k] weight)
      (assoc rep :rejected k))))

(defn make-content-type-weigher
  [req k]
  (->
   (->> (get-in req [:headers "accept"]) parse-csv (map mime/string->media-type)
        (sort-by :weight (comp - compare)))
   best-content-type-acceptable?
   (wrap-weigher :content-type)
   skip-rejected))

(defn get-content-type-weight
  "Given the request and a representation, get the weight of the representation."
  [req rep]
  (let [k :content-type
        weigher-fn (make-content-type-weigher req k)
        rep (weigher-fn rep)]
    (or (get-in rep [:weights k])
        (when (:rejected rep) :rejected))))

(deftest content-type-test
  ;; Basic match
  (is (= (get-content-type-weight
          {:headers {"accept" "text/html"}}
          {:content-type (mime/string->media-type "text/html")})
         [(float 1.0) 3 0 (float 1.0)]))

  ;; Basic match, with multiple options
  (is (= (get-content-type-weight
          {:headers {"accept" "image/png,text/html"}}
          {:content-type (mime/string->media-type "text/html")})
         [(float 1.0) 3 0 (float 1.0)]))

  ;; Basic reject
  (is (= (get-content-type-weight
          {:headers {"accept" "text/html"}}
          {:content-type (mime/string->media-type "text/plain")})
         :rejected))

  ;; Basic reject with multiple options
  (is (= (get-content-type-weight
          {:headers {"accept" "image/png,text/html"}}
          {:content-type (mime/string->media-type "text/plain")})
         :rejected))

  ;; Wildcard match
  (is (= (second (get-content-type-weight
                  {:headers {"accept" "image/png,text/*"}}
                  {:content-type (mime/string->media-type "text/html")}))
         ;; We get a match with a specificty score of 2
         2))

  ;; Specific match beats wildcard
  (is (= (second (get-content-type-weight
                  {:headers {"accept" "image/png,text/*,text/html"}}
                  {:content-type (mime/string->media-type "text/html")}))
         ;; We get a specificty score of 3, indicating we matched on the
         ;; text/html rather than the preceeding text/*
         3))

  ;; TODO: More tests
  )

;; "A request without any Accept header
;; field implies that the user agent
;; will accept any media type in response."
;; -- RFC 7231 Section 5.3.2

;; "A request without any Accept-Charset header field implies
;; that the user agent will accept any charset in response. "
;; -- RFC 7231 Section 5.3.3
