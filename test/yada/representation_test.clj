;; Copyright © 2015, JUXT LTD.

(ns yada.representation-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.util :refer (parse-csv best best-by)]
   [yada.negotiation :as negotiation]
   [yada.representation :as rep]))

(defn- get-highest-content-type-quality
  "Given the request and a representation, get the highest possible
  quality value. Convenience function for independent testing. "
  [req rep]
  (let [k :content-type
        qa (rep/make-content-type-quality-assessor req k)
        rep (qa rep)]
    (or (get-in rep [:qualities k])
        (when (:rejected rep) :rejected))))

(deftest content-type-test

  (testing "Basic match"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html"}}
            {:content-type (mime/string->media-type "text/html")})
           [(float 1.0) 3 0 (float 1.0)])))

  (testing "Basic match, with multiple options"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "image/png,text/html"}}
            {:content-type (mime/string->media-type "text/html")})
           [(float 1.0) 3 0 (float 1.0)])))

  (testing "Basic match, with multiple options and q values"
      (is (= (get-highest-content-type-quality
              {:headers {"accept" "image/png,text/html;q=0.9"}}
              {:content-type (mime/string->media-type "text/html;q=0.8")})
             [(float 0.9) 3 0 (float 0.8)])))

  (testing "Basic reject"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html"}}
            {:content-type (mime/string->media-type "text/plain")})
           :rejected)))

  (testing "Basic reject with multiple options"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "image/png,text/html"}}
            {:content-type (mime/string->media-type "text/plain")})
           :rejected)))

  (testing "Basic reject due to zero accept quality"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html;q=0"}}
            {:content-type (mime/string->media-type "text/html")})
           :rejected)))

  (testing "Basic reject due to zero representation quality"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html"}}
            {:content-type (mime/string->media-type "text/html;q=0")})
           :rejected)))

  (testing "Wildcard match"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "image/png,text/*"}}
             {:content-type (mime/string->media-type "text/html")}) 1)
           ;; We get a match with a specificty score of 2
           2)))

  (testing "Specific match beats wildcard"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "image/png,text/*,text/html"}}
             {:content-type (mime/string->media-type "text/html")}) 1)
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           3)))

  (testing "Specific match beats wildcard, different order"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "text/html,text/*,image/png"}}
             {:content-type (mime/string->media-type "text/html")}) 1)
           3)))

  (testing "Parameter alignment"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html;level=2"}}
            {:content-type (mime/string->media-type "text/html;level=1")})
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           :rejected)))

  (testing "Greater number of parameters matches"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "text/html,text/html;level=1"}}
             {:content-type (mime/string->media-type "text/html;level=1")}) 2)
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           1))))

(defn- get-highest-charset-quality
  "Given the request and a representation, get the highest possible
  quality value. Convenience function for independent testing."
  [req rep]
  (let [k :charset
        qa (rep/make-charset-quality-assessor req k)
        rep (qa rep)]
    (or (get-in rep [:qualities k])
        (when (:rejected rep) :rejected))))

(deftest charset-test

  (testing "Basic match"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "utf-8"}}
            {:charset (charset/to-charset-map "utf-8")})
           [(float 1.0) (float 1.0)])))

  (testing "Basic match with wildcard"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "*"}}
            {:charset (charset/to-charset-map "utf-8")})
           [(float 1.0) (float 1.0)])))

  (testing "Basic match with zero accept quality"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "utf-8;q=0"}}
            {:charset (charset/to-charset-map "utf-8")})
           :rejected)))

  (testing "Basic match with zero rep quality"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "utf-8"}}
            {:charset (charset/to-charset-map "utf-8;q=0")})
           :rejected)))

  (testing "Basic match with wildcard, multiple choices"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "*;q=0.9,utf-8"}}
            {:charset (charset/to-charset-map "utf-8")})
           [(float 1.0) (float 1.0)])))

  (testing "Basic match with wildcard, multiple choices, matches wildcard"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "*;q=0.9,utf-8"}}
            {:charset (charset/to-charset-map "us-ascii")})
           [(float 0.9) (float 1.0)])))

  (testing "Quality values"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "utf-8;q=0.8"}}
            {:charset (charset/to-charset-map "utf-8;q=0.9")})
           [(float 0.8) (float 0.9)])))

  (testing "Multiple choices"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "us-ascii,utf-8;q=0.9,Shift_JIS"}}
            {:charset (charset/to-charset-map "utf-8")})
           [(float 0.9) (float 1.0)])))

  (testing "Multiple choices but none ok"
    (is (= (get-highest-charset-quality
            {:headers {"accept-charset" "us-ascii,Shift_JIS"}}
            {:charset (charset/to-charset-map "utf-8")})
           :rejected))))

;; TODO: q=0 means 'not acceptable' - 5.3.1, so need to code for this

;; TODO: Test encodings - note that encodings can be combined

;; TODO: Test languages

;; TODO: Put the whole thing together, where an accept header corresponds to
;; the construction of a transducer that filters all the possible
;; (not-rejected) representations, then a pro-active pick of the 'best'
;; one, by some determination (content-type first, total quality
;; degradation, etc.)

;; "A request without any Accept header
;; field implies that the user agent
;; will accept any media type in response."
;; -- RFC 7231 Section 5.3.2

;; "A request without any Accept-Charset header field implies
;; that the user agent will accept any charset in response. "
;; -- RFC 7231 Section 5.3.3
