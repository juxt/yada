;; Copyright © 2014-2017, JUXT LTD.

(ns yada.bmh
  "Boyer-Moore-Horspool algorithm. Used to find needles in
  haystacks (and boundary strings in byte buffers).")

;; From https://en.wikipedia.org/wiki/Boyer%E2%80%93Moore%E2%80%93Horspool_algorithm

;; function preprocess(pattern)
;;   T ← new table of 256 integers
;;   for i from 0 to 256 exclusive
;;     T[i] ← length(pattern)
;;   for i from 0 to length(pattern) - 1 exclusive
;; T[pattern[i]] ← length(pattern) - 1 - i
;; return T

(defn- preprocess [^bytes pattern]
  (let [T (byte-array 256)]
    (dotimes [i 255]
      (aset-byte T i (count pattern)))
    (dotimes [i (dec (count pattern))]
      (aset-byte T (aget pattern i) (- (count pattern) 1 i)))
    T))

(defn compute-index [delim]
  {:pattern delim
   :length (count delim)
   :index (preprocess delim)})

;; function search(needle, haystack)
;;   T ← preprocess(needle)
;;   skip ← 0
;;   while length(haystack) - skip ≥ length(needle)
;;     i ← length(needle) - 1
;;     while haystack[skip + i] = needle[i]
;;       if i = 0
;;         return skip
;;       i ← i - 1
;;     skip ← skip + T[haystack[skip + length(needle) - 1]]
;;   return not-found

(defn search [{:keys [length ^bytes index ^bytes pattern]} ^bytes haystack & [limit]]
  (let [limit (or limit (count haystack))]
    (loop [skip (long 0) founds []]
      (if (>= (- limit skip) length)
        (let [found
              (loop [i (dec length)]
                (when (= (aget haystack (+ skip i)) (aget pattern i))
                  (if (= i 0) skip (recur (dec i)))))]
          (recur (long
                  (+ skip (let [x (aget haystack (+ skip length -1))]
                            (if (pos? x) (aget index x) length))))
                 (if found (conj founds found) founds)))
        ;; Return
        founds))))
