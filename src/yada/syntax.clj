(ns yada.syntax
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defn matched [^java.util.regex.Pattern re ^CharSequence s]
  (let [m (re-matcher re s)]
    (when (.matches m)
      m)))

(defn re-group-by-name [^java.util.regex.Matcher matcher ^String name]
  (when matcher
    (.group matcher name)))

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

(def CTL (concat
          (map char (range 0x00 (inc 0x1f)))
          [0x7f]))

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

(def OWS (re-pattern (format "(?:%s)*" (expand-with-character-classes WSP))))

(def BWS OWS) ; "bad" whitespace

(def comma-with-optional-padding (re-pattern (str OWS "," OWS)))

(defprotocol RegExpressable
  (as-regex-str [_] "Return a string that represents the Java regex"))

(extend-protocol RegExpressable
  clojure.lang.ISeq
  (as-regex-str [s] (expand-with-character-classes s))
  clojure.lang.PersistentVector
  (as-regex-str [s] (expand-with-character-classes s))
  String
  (as-regex-str [s] s)
  java.util.regex.Pattern
  (as-regex-str [re] (str re)))

(def token (re-pattern (format "[%s]+" (as-regex-str tchar))))

;; A string of text is parsed as a single value if it is quoted using double-quote marks.

;; obs-text       = %x80-FF
(def obs-text (map char (range 0x80 (inc 0xff))))

;; qdtext         = HTAB / SP /%x21 / %x23-5B / %x5D-7E / obs-text
(def qdtext (concat HTAB SP [0x21] (map char (range 0x23 (inc 0x5b))) (map char (range 0x5d (inc 0x7e))) obs-text))

;; quoted-pair    = "\" ( HTAB / SP / VCHAR / obs-text )
(def quoted-pair (re-pattern (format "\\[%s]" (as-regex-str (concat HTAB SP VCHAR obs-text)))))

;; quoted-string = DQUOTE *( qdtext / quoted-pair ) DQUOTE
(def quoted-string (re-pattern (apply format "%s((?:[%s]|%s)*)%s" (map as-regex-str [DQUOTE qdtext quoted-pair DQUOTE]))))

(def auth-scheme token)

(def token68 (re-pattern (format "[%s]+=*" (as-regex-str (concat ALPHA DIGIT [\- \. \_ \~ \+ \/])))))

;; auth-param     = token BWS "=" BWS ( token / quoted-string )

;; Match an auth-param
(def equals-with-optional-padding (re-pattern (str BWS "=" BWS)))

(def auth-param
  (re-pattern (apply format "(?<lhs>%s)%s((?<token>%s)|%s)"
                     (map as-regex-str [token equals-with-optional-padding token quoted-string]))))

(defn extract-matched-auth-param [^java.util.regex.MatchResult matched]
  (when matched
    ;; We first try the quoted-string sans-quotes (group 4), or if that's nil we try the token value (group 3)
    {::type ::auth-param
     ::name (.group matched 1)
     ::value (or (.group matched 4) (.group matched 3))
     ::value-type (cond (.group matched 4) ::quoted-string
                        (.group matched 3) ::token
                        :else ::unknown)}))

(defn- ^java.util.regex.Matcher advance
  [^java.util.regex.Matcher matcher next-pattern]
  (doto matcher
    (.usePattern next-pattern)
    (.region (.end matcher) (.regionEnd matcher))))

(defn- ^java.util.regex.Matcher with-pattern
  [^java.util.regex.Matcher matcher pattern]
  (doto matcher
    (.usePattern pattern)))

(defn- looking-at
  [^java.util.regex.Matcher matcher]
  (.lookingAt matcher))

(defn element-list [^java.util.regex.Matcher matcher]
  "Implement RFC 7230 #rule extension. Match a comma-separated list of
  the element matched by the given matcher. RFC 7230 Section 7 defines
  the #rule extension used when parsing HTTP headers. Returns a
  collection of java.util.regex.MatchResult instances."
  (let [ ;; Save the element's pattern while we look for commas
        element-pattern (.pattern matcher)]
    (loop [matcher matcher
           matches []]
      (if (looking-at matcher)
        ;; TODO: Make lazy with lazy-seq
        (let [matches (conj matches (.toMatchResult matcher))]
          (if (looking-at (->> comma-with-optional-padding (advance matcher)))
            (recur (advance matcher element-pattern) matches)
            (when (not-empty matches) matches)))
        (when (not-empty matches) matches)))))

;; Match an auth-param#
;; Note: This uses a loop to grab the comma-separated instances.

;; TODO: Test for cases of rogue commas and rogue white-space, (leading, trailing, and otherwise).
#_(let [input "foo = \"bar zip\",zip=AAA  , baz=dig, a=,b=,c=d  "
      matcher (re-matcher auth-param input)]
  (map extract-matched-auth-param (element-list matcher)))


;; Here's how to match against ALL the remaining input - useful below
;; (.region matcher (.end matcher) (.regionEnd matcher))

(def space (re-pattern (as-regex-str SP)))

;;  credentials = auth-scheme [ 1*SP ( token68 / #auth-param ) ]

;; This defines a lookahead to distinguish between a token68 and
;; #auth-param
(def token68-lookahead (re-pattern (str "(?=" token68 OWS "(,|$))")))

(defn parse-credentials [input]
  (when input
    (let [matcher (re-matcher token input)]

      (when (looking-at matcher)
        (let [result {::type ::credentials
                      ::auth-scheme (str/lower-case (.group matcher))}
              parse-remainder
              (fn [result]
                (merge result
                       (if (looking-at (->> (re-pattern (str token68-lookahead token68)) (advance matcher)))
                         {::value (.group matcher)
                          ::value-type ::token68}
                         (when-let [params (as-> matcher %
                                             (with-pattern % auth-param)
                                             (element-list %))]
                           {::value (map extract-matched-auth-param params)
                            ::value-type ::auth-param-list}))))]

          (let [credentials (cond-> result
                              (looking-at (->> space (advance matcher)))
                              parse-remainder)]

            ;; If we managed to find [ 1*SP ( token68 / #auth-param ) ] then advance
            (when (::value credentials)
              (when (not (.hitEnd matcher))
                (.region matcher (.end matcher) (.regionEnd matcher))))

            credentials))))))

(defn format-challenge [m]
  (assert (:scheme m))
  (format
   "%s %s"
   (:scheme m)
   (cond (:params m) (str/join ", " (for [[k v] (:params m)] (format "%s=\"%s\"" (name k) v)))
         (:token68 m) (if (re-matches token68 (:token68 m))
                        (:token68 m)
                        (throw (ex-info "Attempt to format a string as an improper token68"
                                        {:input (:token68 m)
                                         :should-match token68})))
         :else "")))

(defn format-challenges [challenges]
  (str/join ", " (map format-challenge challenges)))


;; TODO: Write rationale for using regexes in this way in syntax.clj.adoc


;;(new String (clojure.data.codec.base64/encode (.getBytes "foo:bar")))
