;; Copyright © 2014-2017, JUXT LTD.

(ns yada.multipart-test
  (:require
   [byte-streams :as b]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [yada.interceptors :as i]
   [yada.util :refer [OWS CRLF]]
   [yada.media-type :as mt]
   [yada.multipart :refer :all]
   [yada.yada :as yada])
  (:import
   [java.io ByteArrayInputStream BufferedReader InputStreamReader]
   [java.nio.charset Charset]))

(deftest parse-content-type-header
  (let [content-type "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"]
    (let [mt (mt/string->media-type content-type)]
      (is (= "multipart/form-data" (:name mt)))
      (is (= "multipart" (:type mt)))
      (is (= "form-data" (:subtype mt)))
      (is (= "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi" (get-in mt [:parameters "boundary"]))))))

(defn to-chunks [s size]
  (b/to-byte-buffers s {:chunk-size size}))

(defn make-content [lines width]
  (apply str
         (repeat lines
                 (apply str (take width (apply concat (repeat (map char (range (int \a) (inc (int \z)))))))))))

(defn part [boundary content]
  ;; Test with different tranport paddings and no transport paddings
  (str (dash-boundary boundary) "   " CRLF content))

(defn close-delimiter [boundary]
  (str (dash-boundary boundary) "--"))

(defn parts [n preamble epilogue boundary content]
  (str
   (when preamble (str preamble CRLF))
   (apply str (repeat n (str (part boundary content) CRLF)))
   (close-delimiter boundary)
   epilogue))

(defn get-chunks [{:keys [boundary preamble epilogue parts# lines# width# chunk-size]}]
  (-> (parts parts# preamble epilogue boundary (make-content lines# width#))
      (to-chunks chunk-size)))

;; Pieces include the preamble. The last piece is (usually) the
;; multipart termination, and may include a postamble. Pieces include
;; the boundary, including the leading CRLF.

;; Boundary detection includes both the CRLF and '--' before the 'ABCD'
;; boundary.

;; TODO: Notify Zach that destructuring should work in d/loop as it does
;; here :-
#_(loop [{:keys [n] :as state} {:n 10}]
  (when (pos? n)
    (println "n is" n)
    (recur (update-in state [:n] dec))))


(defn xf-bytes->content
  "Return a transducer that replaces a :bytes entry byte-array with a String."
  []
  (map (fn [m] (-> m
                  (assoc :content (if-let [b (:bytes m)] (String. b "US-ASCII") "[no bytes]"))
                  (dissoc :bytes)))))

(defn get-parts [{:keys [boundary window-size chunk-size] :as spec}]
  (let [source (-> spec get-chunks s/->source)]
    (->> source
         (parse-multipart boundary window-size chunk-size)
         (s/transform (xf-bytes->content))
         s/stream->seq)))

(defn print-spec [{:keys [boundary window-size] :as spec}]
  (let [source (-> spec get-chunks s/->source)]
    (->> source
         s/stream->seq
         (map b/print-bytes))))

;; DONE: Test against multipart-1
;; TODO: Perhaps examples such as multipart-1 that test different invarients
;; TODO: Create test suite so that TODOs can be tested
;; TODO: Try to trigger 2+ positions on process-boundaries :in-preamble
;; TODO: Use test.check
;; TODO: Integrate into yada resources

(deftest get-parts-test
  (doseq [[chunk-size window-size] [[320 640] [1000 2000]]]
    (let [parts (vec (get-parts {:boundary "ABCD" :preamble "preamble" :epilogue "fin" :parts# 2 :lines# 1 :width# 5 :chunk-size chunk-size :window-size window-size}))]
      (is (= 4 (count parts)))
      (is (= [:preamble :part :part :end] (mapv :type parts)))
      (is (= "preamble" (get-in parts [0 :content])))
      (is (= "abcde" (get-in parts [1 :content])))
      (is (= "abcde" (get-in parts [2 :content])))
      (is (= "fin" (get-in parts [3 :epilogue]))))))

(defn assemble [acc item]
  (letfn [(join [item1 item2]
            (-> item1
                (assoc :type (case [(:type item1) (:type item2)]
                               [:partial :partial-completion] :part))
                (assoc :content (str (:content item1) (:content item2)))))]
    (case (:type item)
      :preamble (conj acc item)
      :part (conj acc item)
      :partial (conj acc item)
      :partial-completion (update-in acc [(dec (count acc))] join item)
      :end (conj acc item))))

(defn slurp-byte-array [res]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (io/copy (io/input-stream res) baos)
    (.toByteArray baos)))

;;(io/resource "yada/multipart-3")

;;(count (slurp (io/resource "yada/multipart-3")))
;;(count (io/input-stream (io/resource "yada/multipart-3")))

(deftest multipart-1-test
  (doseq [{:keys [chunk-size window-size]}
          [{:chunk-size 64 :window-size 160}
           {:chunk-size 640 :window-size 1600}]]

    (let [{:keys [boundary window-size chunk-size] :as spec}
          {:boundary "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"
           :chunk-size chunk-size :window-size window-size}
          chunks (to-chunks (slurp-byte-array (io/resource "yada/multipart-1")) chunk-size)]
      (let [parts
            (->> chunks
                 s/->source
                 (parse-multipart boundary window-size chunk-size)
                 (s/map (fn [m] (-> m
                                    (assoc :content (if-let [b (:bytes m)] (String. b) "[no bytes]"))
                                    (dissoc :bytes :debug))))
                 s/stream->seq
                 (reduce assemble []))]
        (is (= [:part :part :part :end] (mapv :type parts)))
        (is (= "Content-Disposition: form-data; name=\"firstname\"\r\n\r\nJon" (get-in parts [0 :content])))
        (is (= "Content-Disposition: form-data; name=\"surname\"\r\n\r\nPither" (get-in parts [1 :content])))
        (is (= "Content-Disposition: form-data; name=\"phone\"\r\n\r\n1235" (get-in parts [2 :content])))))))

(deftest multipart-2-test
  (doseq [{:keys [chunk-size window-size]}
          [{:chunk-size 64 :window-size 160}
           {:chunk-size 120 :window-size 360}
           {:chunk-size 640 :window-size 1600}]]

    (let [{:keys [boundary window-size chunk-size] :as spec}
          {:boundary "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"
           :chunk-size chunk-size :window-size window-size}]
      (let [parts
            (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-2")) chunk-size))
                 (parse-multipart boundary window-size chunk-size)
                 (s/map (fn [m] (-> m
                                    (assoc :content (if-let [b (:bytes m)] (String. b) "[no bytes]"))
                                    (dissoc :bytes :debug))))
                 s/stream->seq
                 (reduce assemble []))]
        (is (= [:preamble :part :part :part :end] (mapv :type parts)))
        (is (= "This is some preamble" (get-in parts [0 :content])))
        (is (= "Content-Disposition: form-data; name=\"firstname\"\r\n\r\nMalcolm" (get-in parts [1 :content])))
        (is (= "Content-Disposition: form-data; name=\"surname\"\r\n\r\nSparks" (get-in parts [2 :content])))
        (is (= "Content-Disposition: form-data; name=\"phone\"\r\n\r\n1234" (get-in parts [3 :content])))
        (is (= "\r\nHere is some epilogue" (get-in parts [4 :epilogue])))))))

;; Parsing of Content-Disposition.
;; Headers need to be parsed as per RFC 5322. These are not your normal HTTP headers, necessarily, so RFC 7230 does not apply.

;; Note header field names must be US-ASCII chars in the range 33 to 126, except colon
;; inclusive (https://tools.ietf.org/html/rfc5322#section-2.2)

;; 2.5 secs, where's the time being taken up??
;; With reduced buffer copies (see boyer moore implementation), we get ~450 ms - still far too large for a 339773 byte photo

(deftest parse-content-disposition-header-test
  (let [parsed-header (parse-content-disposition-header "type;  foo=bar;  a=\"b\";\t  c=abc")]
    (is (= "type" (:type parsed-header)))
    (is (= "bar" (get-in parsed-header [:params "foo"])))
    (is (= "b" (get-in parsed-header [:params "a"])))
    (is (= "abc" (get-in parsed-header [:params "c"]))))
  (let [parsed-header (parse-content-disposition-header "form-data; name=\"file\"; filename=\"data.csv\"")]
    (is (= "form-data" (:type parsed-header)))
    (is (= "file" (get-in parsed-header [:params "name"])))
    (is (= "data.csv" (get-in parsed-header [:params "filename"])))))

(def image-size 339773)

(def header-size (count (str "Content-Disposition: form-data; name=\"image\"" CRLF CRLF)))

(deftest reduce-multipart-partial-pieces-test
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (let [parts
          (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-3")) chunk-size))
               (parse-multipart "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi" (:window-size spec) (:chunk-size spec))
               (s/reduce reduce-piece {:consumer (->DefaultPartConsumer) :state {:parts []}})
               deref :parts)]
      (is (= [:part :part :part :part] (mapv :type parts)))
      (let [last-part (last parts)]
        (is (= 7 (count (:pieces last-part))))
        (is (= [65130 49154 49152 49152 49152 49152 28929] (mapv :bytes (:pieces last-part))))
        (is (= (+ header-size image-size) (apply + (mapv :bytes (:pieces last-part)))))
        (is (= (+ header-size image-size) (count (:bytes last-part))))))))

(deftest reduce-multipart-formdata-test
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (let [parts
          (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-3")) chunk-size))
               (parse-multipart "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi" (:window-size spec) (:chunk-size spec))
               (s/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))
               (s/reduce reduce-piece {:consumer (->DefaultPartConsumer) :state {:parts []}})
               deref
               :parts
               )]

      (is (= [:part :part :part :part] (mapv :type parts)))
      (let [p (first parts)]
        (is (= (count "Malcolm") (- (count (:bytes p)) (:body-offset p)))))
      (let [p (last parts)]
        (is (= [65130 49154 49152 49152 49152 49152 28929] (mapv :bytes (:pieces p))))
        (is (= (+ header-size image-size) (count (:bytes p))))
        (is (= 48 (:body-offset p)))))))

(deftest big-file-test
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (let [parts
          (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-4")) chunk-size))
               (parse-multipart "IIKwJwJU_tJT6hjEW8f_JHXCqjFM_zjBrl936VG" (:window-size spec) (:chunk-size spec))
               (s/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))
               (s/reduce reduce-piece {:consumer (->DefaultPartConsumer) :state {:parts []}})
               deref
               :parts
               )]

      (is (= [:part] (mapv :type parts))))))

(deftest big-file-test-2
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (let [parts
          (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-5")) chunk-size))
               (parse-multipart "E4KpbkJMX9ceMQct1J0gOStbUStnW3N" (:window-size spec) (:chunk-size spec))
               (s/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))
               (s/reduce reduce-piece {:consumer (->DefaultPartConsumer) :state {:parts []}})
               deref
               :parts)]

      (is (= [:part] (mapv :type parts))))))


(deftest convenience-function-test
  (let [ctx
        @(let [body (slurp-byte-array (io/resource "yada/multipart-6"))]
          (let [ctx {:method :post
                     :request {:headers
                               {"content-length" (str (alength body))
                                "content-type" "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"}
                               :body (java.io.ByteArrayInputStream. body)}
                     :resource (yada/resource {:methods
                                               {:post
                                                {:consumes "multipart/form-data"
                                                 ;;:parameters {:body body-schema}
                                                 :response ""}}})}]
            (i/process-request-body ctx)))]

    (is (find-part ctx "firstname"))
    (is (find-part ctx "phone"))
    (is (not (find-part ctx "dummy")))

    (is (= "text/html" (:name (part-content-type (find-part ctx "firstname")))))
    (is (= "text/plain" (:name (part-content-type (find-part ctx "phone")))))
    (is (nil? (part-content-type (find-part ctx "email"))))

    (is (= "<h1>Malcolm</h1>" (part-string (find-part ctx "firstname"))))
    (is (= "1234" (part-string (find-part ctx "phone"))))
    (is (= "malcolm@juxt.pro" (part-string (find-part ctx "email"))))
    (is (nil? (part-string (find-part ctx "dummy"))))))
