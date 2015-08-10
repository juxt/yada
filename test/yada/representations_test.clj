;; Copyright © 2015, JUXT LTD.

(ns yada.representations-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.util :refer (parse-csv)]
   [yada.negotiation :as negotiation]))


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
  (let [accepts (->> (get-in req [:headers "accept"]) parse-csv (map mime/string->media-type)
                     (sort-by :weight (comp - compare)))]
    (->
     (fn [rep]
       (some (partial content-type-acceptable? (:content-type rep)) accepts))
     (wrap-weigher :content-type)
     skip-rejected)))

;; Add lots of tests for this

(deftest content-type-test
  (let [req {:headers {"accept" "text/xml;q=0.8"}}
        f (make-content-type-weigher req :content-type)]

    (is (= (get-in (f {:content-type (mime/string->media-type "text/xml")})
                   [:weights :content-type])
           [(float 0.8) 3 0 (float 1.0)]))))

;; "A request without any Accept header
;; field implies that the user agent
;; will accept any media type in response."
;; -- RFC 7231 Section 5.3.2

;; "A request without any Accept-Charset header field implies
;; that the user agent will accept any charset in response. "
;; -- RFC 7231 Section 5.3.3
