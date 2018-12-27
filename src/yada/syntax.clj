(ns yada.syntax
  (:require
   [clojure.set :as set]
   [clojure.string :as str]))

;; Some security attacks exploit non-conforming implementations of
;; HTTP syntax. This namespace is intended to protect yada from bad
;; input data as well as ensure it behaves correctly in generating
;; syntax.

;; From RFC 4234

(def ALPHA
  (map char (concat
             (range 0x41 (inc 0x5A))
             (range 0x61 (inc 0x7A)))))

(def CHAR
  (map char (range 0x01 (inc 0x7F))))

(def CR [(char 0x0D)])

(def DIGIT
  (map char (range 0x30 (inc 0x39))))

(def DQUOTE [(char 0x22)])

(def HEXDIG (conj DIGIT \A \B \C \D \E \F))

(def HTAB [(char 0x09)])

(def LF [(char 0x0A)])

(def OCTET (map char (range 0x00 (inc 0xFF))))

(def SP  [(char 0x20)])

(def WSP (distinct (concat SP HTAB)))

;; Visible (printing) characters
(def VCHAR (map char (range 0x21 (inc 0x7E))))


;; RFC 7230

(def tchar (distinct (concat
                      ALPHA DIGIT
                      [\! \# \$ \% \& \' \* \+ \- \. \^ \_ \` \| \~])))


(defn expand-with-character-classes
  "Take a collection of characters and return a string representing the
  concatenation of the Java regex characters, including the use
  character classes wherever possible without conformance loss. This
  function is not designed for performance and should be called to
  prepare systems prior to the handling of HTTP requests."
  [s]
  (let [{:keys [classes remaining]}
        (reduce
         (fn [{:keys [remaining] :as acc} {:keys [class set]}]
           (cond-> acc
             (set/subset? set remaining) (-> (update :classes conj class)
                                             (update :remaining set/difference set))))
         {:remaining (set s) :classes []}

         [{:class "Alnum" :set (set (concat ALPHA DIGIT))}
          {:class "Alpha" :set (set ALPHA)}
          {:class "Digit" :set (set DIGIT)}
          {:class "Blank" :set (set WSP)}])]

    (str/join "" (concat
                  (map #(format "\\p{%s}" %) classes)
                  (map #(str "\\x" %) (map #(Integer/toHexString (int %)) remaining))))))
