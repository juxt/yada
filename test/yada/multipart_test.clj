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
   [yada.media-type :as mt]
   [yada.multipart :refer [parse-multipart]]))

(deftest parse-content-type-header
  (let [content-type "multipart/form-data; boundary=----WebKitFormBoundaryZ3oJB7WHOBmOjrEi"]
    (given (mt/string->media-type content-type)
      :name := "multipart/form-data"
      :type := "multipart"
      :subtype := "form-data"
      [:parameters "boundary"] := "----WebKitFormBoundaryZ3oJB7WHOBmOjrEi")))

(defn to-chunks [s size]
  (b/to-byte-buffers s {:chunk-size size}))

(def CRLF "\r\n")

(defn make-content [lines width]
  (apply str
         (repeat lines
                 (apply str (take width (apply concat (repeat (map char (range (int \a) (inc (int \z)))))))))))

(defn dash-boundary [boundary]
  (str "--" boundary))

(defn part [boundary content]
  ;; Test with different tranport paddings and no transport paddings
  (str (dash-boundary boundary) "   " CRLF content))

(defn close-delimiter [boundary]
  (str (dash-boundary boundary) "--"))

(defn parts [n preamble boundary content]
  (str
   (when preamble (str preamble CRLF))
   (apply str (repeat n (str (part boundary content) CRLF)))
   (close-delimiter boundary)))

(defn get-chunks [{:keys [boundary preamble parts# lines# width# chunk-size]}]
  (-> (parts parts# preamble boundary (make-content lines# width#))
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

(defn get-parts* [{:keys [boundary chunk-size window-size] :as spec}]
  (let [source (-> spec get-chunks s/->source)]
    (->> source
         (parse-multipart boundary window-size chunk-size)
         (s/map (fn [m] (-> m
                           (assoc :content (if-let [b (:bytes m)] (String. b) "[no bytes]"))
                           (dissoc :bytes))))
         s/stream->seq)))

(defn print-spec [{:keys [boundary chunk-size window-size] :as spec}]
  (let [source (-> spec get-chunks s/->source)]
    (->> source
         s/stream->seq
         (map b/print-bytes))))

;; TODO: Need to ensure that the boundary is (significantly) smaller
;; than buffer size. This needs to be asserted by parse-multipart before
;; any processing happens. This is a potential attack vector.

(get-parts* {:boundary "ABCD"
             #_:preamble #_"a long preamble that really should push us into another buffer, causing death and destruction!"
             :parts# 2
             :lines# 2
             :width# 10
             :chunk-size 32
             :window-size 64})

;; DONE: Emit epilogues {:type :end :epilogue "abc"}
;; TODO: Add epilogues to test to ensure they're being extracted ok
;; TODO: Fix API first, then write tests.
;; TODO: Test against multipart-1
;; TODO: Perhaps examples such as multipart-1 that test different invarients
;; TODO: Create test suite so that TODOs can be tested
;; TODO: Try to trigger 2+ positions on process-boundaries :in-preamble
;; TODO: When we increase chunk-size to 320 and window-size to 640, the preamble disappears
;; TODO: Commit
;; TODO: Use test.check
;; TODO: Integrate into resources

(deftest get-parts*-test
  (given
    (get-parts* {:boundary "ABCD" :preamble "preamble" :parts# 2 :lines# 1 :width# 5 :chunk-size 16 :window-size 48})
    count := 4
    (partial map :type) := [:preamble :part :part :end]
    [0 :content] := "preamble"
    [1 :content] := "abcde"
    [2 :content] := "abcde"
    ))
