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
  (as-regex-str [re] (str re))
  clojure.lang.PersistentHashSet
  (as-regex-str [s] (as-regex-str (seq s))))

(def token (re-pattern (format "[%s]+" (as-regex-str tchar))))

(def obs-text (map char (range 0x80 (inc 0xff))))

(def qdtext (concat HTAB SP [0x21] (map char (range 0x23 (inc 0x5b))) (map char (range 0x5d (inc 0x7e))) obs-text))

(def quoted-pair (re-pattern (format "\\[%s]" (as-regex-str (concat HTAB SP VCHAR obs-text)))))

(def quoted-string (re-pattern (apply format "%s((?:[%s]|%s)*)%s" (map as-regex-str [DQUOTE qdtext quoted-pair DQUOTE]))))

(def auth-scheme token)

(def token68 (re-pattern (format "[%s]+=*" (as-regex-str (concat ALPHA DIGIT [\- \. \_ \~ \+ \/])))))

(def equals-with-optional-padding (re-pattern (str BWS "=" BWS)))

(def space (re-pattern (as-regex-str SP)))

(def cookie-octet
  (map char
       (concat [0x21]
               (range 0x23 (inc 0x2B))
               (range 0x2D (inc 0x3A))
               (range 0x3C (inc 0x5B))
               (range 0x5D (inc 0x7E)))))

;; This defines a lookahead to distinguish between a token68 and
;; #auth-param
(def token68-lookahead (re-pattern (str "(?=" token68 OWS "(,|$))")))

(def auth-param
  (re-pattern (apply format "(?<lhs>%s)%s((?<token>%s)|%s)"
                     (map as-regex-str [token equals-with-optional-padding token quoted-string]))))

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
  collection of java.util.regex.MatchResult instances. Note: This uses
  a loop to grab the comma-separated instances."
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

;; Cookies - see RFC 6265

(def cookie-name token)

(def cookie-value (re-pattern (apply format "(?:%s([%s]*)%s|([%s]*))" (map as-regex-str [DQUOTE cookie-octet DQUOTE cookie-octet]))))

(def cookie-pair (re-pattern (apply format "(%s)=%s" (map as-regex-str [cookie-name cookie-value]))))

(def semicolon-then-space (re-pattern (str ";" (as-regex-str SP))))

(defn parse-cookie-header [input]
  (when input
    (let [matcher (re-matcher cookie-pair input)]
      (loop [matcher matcher
             matches []]
        (if (looking-at matcher)
          (let [matches
                (conj matches
                      (let [mr (.toMatchResult matcher)]
                        {::type ::cookie
                         ::name (.group mr 1)
                         ::value (or (.group mr 2) (.group mr 3))
                         ::quotes? (if (.group mr 2) true false)}))]
            (if (looking-at (advance matcher semicolon-then-space))
              (recur (advance matcher cookie-pair) matches)
              (when (not-empty matches) matches)))
          (when (not-empty matches) matches))))))

(def rfc822-date-time (re-pattern "(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun), \\d{2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec) \\d{4} \\d{2}:\\d{2}:\\d{2} (?:UT|GMT|EST|EDT|CST|CDT|MST|MDT|PST|PDT|Z)"))

(def label
  (re-pattern
   (apply format "%s[%s]*%s"
          (map as-regex-str
               [(concat ALPHA DIGIT) ; RFC 1123
                (concat ALPHA DIGIT [\-])
                (concat ALPHA DIGIT)]))))

(def subdomain
  (re-pattern
   (apply format "%s(?:%s%s)*" (map as-regex-str [label
                                                  [0x2e]
                                                  label]))))

(def path (re-pattern (format "[%s]+" (as-regex-str (set/difference
                                                     (set (map char (range 0x00 (inc 0xff))))
                                                     (set CTL)
                                                     #{\;}
                                                     )))))

(def set-cookie-attrs
  {:expires "Expires"
   :max-age "Max-Age"
   :domain "Domain"
   :path "Path"
   :secure "Secure"
   :http-only "HttpOnly"})

(defn format-set-cookie [[name v]]
  (letfn [(encode-attributes [cv]
            (apply str
                   (for [k [:expires :max-age :domain :path :secure :http-only]]
                     (when-let [v (get cv k)]
                       (if (#{:secure :http-only} k)
                         (format "; %s" (set-cookie-attrs k))
                         (format "; %s=%s" (set-cookie-attrs k) v))))))]
    (format "%s=%s%s" name (:value v) (encode-attributes v))))

;; Authentication

(defn extract-matched-auth-param [^java.util.regex.MatchResult matched]
  (when matched
    ;; We first try the quoted-string sans-quotes (group 4), or if that's nil we try the token value (group 3)
    {::type ::auth-param
     ::name (.group matched 1)
     ::value (or (.group matched 4) (.group matched 3))
     ::value-type (cond (.group matched 4) ::quoted-string
                        (.group matched 3) ::token
                        :else ::unknown)}))

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

          (let [credentials
                (cond-> result
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
