;; Copyright © 2015, JUXT LTD.

(ns yada.multipart-test
  (:require
   [byte-streams :as b]
   [clj-index.core :refer [bm-index match]]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [clojure.pprint :refer [pprint]]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [juxt.iota :refer [given]]
   [yada.util :refer [OWS CRLF]]
   [yada.media-type :as mt]
   [yada.multipart :refer :all])
  (:import
   [java.io ByteArrayInputStream BufferedReader InputStreamReader]
   [java.nio.charset Charset]))

(deftest parse-content-type-header
  (let [content-type "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"]
    (given (mt/string->media-type content-type)
      :name := "multipart/form-data"
      :type := "multipart"
      :subtype := "form-data"
      [:parameters "boundary"] := "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")))

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
  (doseq [[chunk-size window-size] [[16 48] [320 640] [10 50] [1000 2000]]]
    (given
      (get-parts (merge {:boundary "ABCD" :preamble "preamble" :epilogue "fin" :parts# 2 :lines# 1 :width# 5}
                        {:chunk-size chunk-size :window-size window-size}))
      count := 4
      (partial map :type) := [:preamble :part :part :end]
      [0 :content] := "preamble"
      [1 :content] := "abcde"
      [2 :content] := "abcde"
      [3 :epilogue] := "fin")))

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
      (given
        (->> chunks
             s/->source
             (parse-multipart boundary window-size chunk-size)
             (s/map (fn [m] (-> m
                               (assoc :content (if-let [b (:bytes m)] (String. b) "[no bytes]"))
                               (dissoc :bytes :debug))))
             s/stream->seq
             (reduce assemble []))
        (partial map :type) := [:part :part :part :end]
        [0 :content] := "Content-Disposition: form-data; name=\"firstname\"\r\n\r\nJon"
        [1 :content] := "Content-Disposition: form-data; name=\"surname\"\r\n\r\nPither"
        [2 :content] := "Content-Disposition: form-data; name=\"phone\"\r\n\r\n1235"))))

(deftest multipart-2-test
  (doseq [{:keys [chunk-size window-size]}
          [{:chunk-size 64 :window-size 160}
           {:chunk-size 120 :window-size 360}
           {:chunk-size 640 :window-size 1600}]]

    (let [{:keys [boundary window-size chunk-size] :as spec}
          {:boundary "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"
           :chunk-size chunk-size :window-size window-size}]
      (given
        (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-2")) chunk-size))
             (parse-multipart boundary window-size chunk-size)
             (s/map (fn [m] (-> m
                               (assoc :content (if-let [b (:bytes m)] (String. b) "[no bytes]"))
                               (dissoc :bytes :debug))))
             s/stream->seq
             (reduce assemble []))
        (partial map :type) := [:preamble :part :part :part :end]
        [0 :content] := "This is some preamble"
        [1 :content] := "Content-Disposition: form-data; name=\"firstname\"\r\n\r\nMalcolm"
        [2 :content] := "Content-Disposition: form-data; name=\"surname\"\r\n\r\nSparks"
        [3 :content] := "Content-Disposition: form-data; name=\"phone\"\r\n\r\n1234"
        [4 :epilogue] := "\r\nHere is some epilogue"))))

;; Parsing of Content-Disposition.
;; Headers need to be parsed as per RFC 5322. These are not your normal HTTP headers, necessarily, so RFC 7230 does not apply.

;; Note header field names must be US-ASCII chars in the range 33 to 126, except colon
;; inclusive (https://tools.ietf.org/html/rfc5322#section-2.2)

;; 2.5 secs, where's the time being taken up??
;; With reduced buffer copies (see boyer moore implementation), we get ~450 ms - still far too large for a 339773 byte photo

(deftest parse-content-disposition-header-test
  (given (parse-content-disposition-header "type;  foo=bar;  a=\"b\";\t  c=abc")
    :type := "type"
    [:params "foo"] := "bar"
    [:params "a"] := "b"
    [:params "c"] := "abc"))

(def image-size 339773)

(def header-size (count (str "Content-Disposition: form-data; name=\"image\"" CRLF CRLF)))

(deftest reduce-multipart-partial-pieces-test
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (given
      (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-3")) chunk-size))
           (parse-multipart "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi" (:window-size spec) (:chunk-size spec))
           (s/reduce reduce-piece {:receiver (->DefaultPartReceiver) :state {:parts []}})
           deref)
      [:parts (partial map :type)] := [:part :part :part :part]
      [:parts last :pieces count] := 7
      [:parts last :pieces (partial map :bytes)] := [65130 49154 49152 49152 49152 49152 28929]
      [:parts last :pieces (partial map :bytes) (partial apply +)] := (+ header-size image-size)
      [:parts last :bytes count] := (+ header-size image-size))))

(deftest reduce-multipart-formdata-test
  (let [[chunk-size window-size] [16384 (* 4 16384)]
        spec {:chunk-size chunk-size :window-size window-size}]
    (given
      (->> (s/->source (to-chunks (slurp-byte-array (io/resource "yada/multipart-3")) chunk-size))
           (parse-multipart "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi" (:window-size spec) (:chunk-size spec))
           (s/transform (comp (xf-add-header-info) (xf-parse-content-disposition)))
           (s/reduce reduce-piece {:receiver (->DefaultPartReceiver) :state {:parts []}})
           deref)
      [:parts (partial map :type)] := [:part :part :part :part]
      [:parts first (juxt (comp count :bytes) :body-offset) (partial apply -)] := (count "Malcolm")
      [:parts last :pieces (partial map :bytes)] := [65130 49154 49152 49152 49152 49152 28929]
      [:parts last :bytes count] := (+ header-size image-size)
      [:parts last :body-offset] := 48)))
