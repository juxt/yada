;; Copyright © 2015, JUXT LTD.

(ns yada.representation-test
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [schema.test :as st]
   [yada.charset :as charset]
   [yada.media-type :as mt]
   [yada.util :refer (parse-csv best best-by http-token OWS)]
   [yada.representation :as rep]))

(defn- get-highest-content-type-quality
  "Given the request and a representation, get the highest possible
  quality value for the representation's content-type."
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
            {:content-type (mt/string->media-type "text/html")})
           [(float 1.0) 3 0 (float 1.0)])))

  (testing "Basic match, with multiple options"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "image/png,text/html"}}
            {:content-type (mt/string->media-type "text/html")})
           [(float 1.0) 3 0 (float 1.0)])))

  (testing "Basic match, with multiple options and q values"
      (is (= (get-highest-content-type-quality
              {:headers {"accept" "image/png,text/html;q=0.9"}}
              {:content-type (mt/string->media-type "text/html;q=0.8")})
             [(float 0.9) 3 0 (float 0.8)])))

  (testing "Basic reject"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html"}}
            {:content-type (mt/string->media-type "text/plain")})
           :rejected)))

  (testing "Basic reject with multiple options"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "image/png,text/html"}}
            {:content-type (mt/string->media-type "text/plain")})
           :rejected)))

  (testing "Basic reject due to zero accept quality"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html;q=0"}}
            {:content-type (mt/string->media-type "text/html")})
           :rejected)))

  (testing "Basic reject due to zero representation quality"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html"}}
            {:content-type (mt/string->media-type "text/html;q=0")})
           :rejected)))

  (testing "Wildcard match"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "image/png,text/*"}}
             {:content-type (mt/string->media-type "text/html")}) 1)
           ;; We get a match with a specificity score of 2
           2)))

  (testing "Wildcard type mismatch"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/*"}}
            {:content-type (mt/string->media-type "image/png")})
           :rejected)))

  (testing "Specific match beats wildcard"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "image/png,text/*,text/html"}}
             {:content-type (mt/string->media-type "text/html")}) 1)
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           3)))

  (testing "Specific match beats wildcard, different order"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "text/html,text/*,image/png"}}
             {:content-type (mt/string->media-type "text/html")}) 1)
           3)))

  (testing "Parameter alignment"
    (is (= (get-highest-content-type-quality
            {:headers {"accept" "text/html;level=2"}}
            {:content-type (mt/string->media-type "text/html;level=1")})
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           :rejected)))

  (testing "Greater number of parameters matches"
    (is (= ((get-highest-content-type-quality
             {:headers {"accept" "text/html,text/html;level=1"}}
             {:content-type (mt/string->media-type "text/html;level=1")}) 2)
           ;; We get a specificty score of 3, indicating we matched on the
           ;; text/html rather than the preceeding text/*
           1))))

(defn- get-highest-charset-quality
  "Given the request and a representation, get the highest possible
  qvalue for the representation's charset."
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

(deftest parse-encoding-test
  (testing "basic"
    (is (= (rep/parse-encoding "abc;q=0.90") {:coding "abc" :quality (float 0.9)})))
  (testing "qvalue defaults to 1.0"
    (is (= (rep/parse-encoding "abc") {:coding "abc" :quality (float 1.0)})))
  (testing "qvalue must be a number"
    (is (nil? (rep/parse-encoding "abc;q=foo"))))
  (testing "qvalue cannot contain more than 3 decimal places"
    (is (nil? (rep/parse-encoding "abc;q=0.1234"))))
  (testing "qvalue cannot be more than 1"
    (is (nil? (rep/parse-encoding "abc;q=1.001")))))

(defn- get-highest-encoding-quality
  "Given the request and a representation, get the highest possible
  qvalue for the representation's encoding."
  [req rep]
  (let [k :encoding
        qa (rep/make-encoding-quality-assessor req k)
        rep (qa rep)]
    (or (get-in rep [:qualities k])
        (when (:rejected rep) :rejected))))

(deftest encoding-test

  (testing "Representation has no encoding, it is acceptable, rule 2"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip"}}
            {})
           [1.0 1.0])))

  (testing "... except when not"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip, identity;q=0"}}
            {})
           :rejected)))

  (testing "... except when not with wildcard"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip, *;q=0"}}
            {})
           :rejected)))

  (testing "... except when not"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip, identity;q=0.5, *;q=0"}}
            {})
           [0.5 1.0])))

  (testing "Basic match"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip, compress"}}
            {:encoding (rep/parse-encoding "gzip;q=0.7")})
           [(float 1.0) (float 0.7)])))

  (testing "Basic reject"
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" "gzip, compress"}}
            {:encoding (rep/parse-encoding "deflate;q=0.4")})
           :rejected)))

  (testing "Empty accept encoding header field value"
    ;; The client does NOT want any codings
    (is (= (get-highest-encoding-quality
            {:headers {"accept-encoding" ""}}
            {:encoding (rep/parse-encoding "deflate,gzip;q=0.4")})
           :rejected))))

(deftest parse-language-test
  (testing "basic"
    (is (= (rep/parse-language "en-US;q=0.8")
           {:language ["en" "us"] :quality (float 0.8)})))
  (testing "wildcard"
    (is (= (rep/parse-language "en-*")
           {:language ["en" "*"] :quality (float 1.0)}))))

(defn- lang-matches? [rep accepts]
  (rep/lang-matches?
   (:language (rep/parse-language rep))
   (:language (rep/parse-language accepts))))

(deftest lang-matches-test
  (is (lang-matches? "en" "en"))
  (is (lang-matches? "en" "en-US"))
  (is (lang-matches? "en-US" "en-US"))
  (is (not (lang-matches? "en-US" "en")))
  (is (lang-matches? "en-US" "en-*"))
  (is (not (lang-matches? "en-US-fr" "en-*")))
  (is (lang-matches? "en-US-fr" "en-*-fr"))
  (is (lang-matches? "en-US-fr" "en-US-fr")))

(defn- get-highest-language-quality
  "Given the request and a representation, get the highest possible
  qvalue for the representation's language-tag."
  [req rep]
  (let [k :language
        qa (rep/make-language-quality-assessor req k)
        rep (qa rep)]
    (or (get-in rep [:qualities k])
        (when (:rejected rep) :rejected))))

(st/deftest language-test

  (testing "Basic match"
    (is (= (get-highest-language-quality
            {:headers {"accept-language" "en"}}
            {:language (rep/parse-language "en")})
           [(float 1.0) (float 1.0)])))

  (testing "Wildcard match"
    (is (= (get-highest-language-quality
            {:headers {"accept-language" "en-*;q=0.8"}}
            {:language (rep/parse-language "en-US;q=0.7")})
           [(float 0.8) (float 0.7)])))

  (testing "Reject"
    (is (= (get-highest-language-quality
            {:headers {"accept-language" "en"}}
            {:language (rep/parse-language "en-US")})
           :rejected))))

;; TODO: Put the whole thing together, where an accept header corresponds to
;; the construction of a transducer that filters all the possible
;; (not-rejected) representations, then a pro-active pick of the 'best'
;; one, by some determination (content-type first, total quality
;; degradation, etc.)

;; TODO: Special case: when the accept-encoding header field value is
;; empty, the encoding is set to identity, no matter what. When creating
;; a list of representations, always create identity versions. See 5.3.4
;; This can be done by interpretting :encoding "gzip, deflate" to be
;; :encoding "gzip, deflate, identity;q=0.001" and applying
;; clojure.core/distinct on the final set of representations.

;; "A request without any Accept header
;; field implies that the user agent
;; will accept any media type in response."
;; -- RFC 7231 Section 5.3.2

;; "A request without any Accept-Charset header field implies
;; that the user agent will accept any charset in response. "
;; -- RFC 7231 Section 5.3.3

(deftest ^{:doc "If you find bugs in yada with end-to-end (proactive)
  content negotiation, here's a good place to put a test."}
  select-representation-test

  (testing "Best quality charset"
    (is (= (rep/select-representation
            {:headers {"accept" "text/html"}}
            [{:content-type (mt/string->media-type "text/html")
              :charset (charset/to-charset-map "windows-1255;q=0.9")}
             {:content-type (mt/string->media-type "text/html")
              :charset (charset/to-charset-map "utf-8")}])
           {:content-type (mt/string->media-type "text/html")
            :charset (charset/to-charset-map "utf-8")})))

  (let [reps [{:content-type (mt/string->media-type "text/html")
               :charset (charset/to-charset-map "utf-8")}
              {:content-type (mt/string->media-type "text/xml;q=0.9")
               :charset (charset/to-charset-map "utf-8")}
              {:content-type (mt/string->media-type "text/xml;q=0.9")
               :charset (charset/to-charset-map "us-ascii")}
              {:content-type (mt/string->media-type "image/png")}]]

    (testing "No headers. Implied Accept: */*"
      (is (= (rep/select-representation
              {:headers {}} reps)
             {:content-type (mt/string->media-type "text/html")
              :charset (charset/to-charset-map "utf-8")})))

    (testing "Basic match"
      (is (= (rep/select-representation
              {:headers {"accept" "text/html"}} reps)
             {:content-type (mt/string->media-type "text/html")
              :charset (charset/to-charset-map "utf-8")}))

      (is (= (rep/select-representation {:headers {"accept" "text/xml"}} reps)
             {:content-type (mt/string->media-type "text/xml;q=0.9")
              :charset (charset/to-charset-map "utf-8")}))

      (is (= (rep/select-representation
              {:headers {"accept" "image/png"}} reps)
             {:content-type (mt/string->media-type "image/png")})))

    (testing "Wildcard match"
      (is (= (rep/select-representation
              {:headers {"accept" "image/*"}}
              reps)
             {:content-type (mt/string->media-type "image/png")})))


    ))


;;                     "accept-charset" "utf-8"
;;                     "accept-encoding" "gzip"
;;                     "accept-language" "en"
