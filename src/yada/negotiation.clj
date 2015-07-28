(ns yada.negotiation
  (:require
   [yada.mime :as mime]
   [yada.charset :as cs]
   [yada.util :refer (http-token)]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.string :as str]
   [schema.core :as s]
   [clojure.set :as set]
   )
  (:import [yada.charset CharsetMap]
           [yada.mime MediaTypeMap]))

;; ------------------------------------------------------------------------
;; Content types

(defn- content-type-acceptable?
  "Compare a single acceptable mime-type (extracted from an Accept
  header) and a candidate. If the candidate is acceptable, return a
  sortable vector [acceptable candidate weight1 weight2]. Weight1
  prefers specificity, e.g. prefers text/html over text/* over
  */*. Weight2 gives preference to candidates with a greater number of
  parameters, which preferes text/html;level=1 over text/html. This
  meets the criteria in the HTTP specifications. Although the preference
  that should result with multiple parameters is not specified formally,
  candidates that "
  [acceptable candidate]
  (when
      (= (:parameters acceptable)
         (select-keys (:parameters candidate) (keys (:parameters acceptable))))
    (cond
      (and (= (:type acceptable) (:type candidate))
           (= (:subtype acceptable) (:subtype candidate)))
      [acceptable candidate {:weight 3} {:weight (count (:parameters candidate))}]

      (and (= (:type acceptable) (:type candidate))
           (= (:subtype acceptable) "*"))
      [acceptable candidate {:weight 2} {:weight (count (:parameters candidate))}]

      (and (= (mime/media-type acceptable) "*/*"))
      [acceptable candidate {:weight 1} {:weight (count (:parameters candidate))}])))

(defn- any-content-type-acceptable? [acceptables candidate]
  (some #(content-type-acceptable? % candidate) acceptables))

(defn- negotiate-content-type*
  "Return the best content type via negotiation."
  [acceptables candidates]
  (->> candidates
       (keep (partial any-content-type-acceptable? acceptables))
       (sort-by #(vec (map :weight %)))
       reverse ;; highest weight wins
       first ;; winning pair
       second ;; extract the server provided mime-type
       ;; Commented because the caller needs this in order to sort now
       ;; (#(dissoc % :weight))
       ))

(defn negotiate-content-type [accept-header available]
  (negotiate-content-type*
   (map mime/string->media-type (map str/trim (str/split accept-header #"\s*,\s*")))
   available))

;; ------------------------------------------------------------------------
;; Charsets

(defn- acceptable-charset? [acceptable-charset candidate]
  (when
      (or (= (cs/charset acceptable-charset) "*")
          (and
           (some? (cs/charset acceptable-charset))
           (= (cs/charset acceptable-charset)
              (cs/charset candidate)))
          ;; Finally, let's see if their canonical names match
          (and
           (some? (cs/canonical-name acceptable-charset))
           (= (cs/canonical-name acceptable-charset)
              (cs/canonical-name candidate))))
    [acceptable-charset candidate]))

(defn- any-charset-acceptable? [acceptables candidate]
  (if (nil? acceptables)
    [candidate candidate] ; no header means the user-agent accepts 'any charset' in response - rfc7231.html#section-5.3.3
    (some #(acceptable-charset? % candidate) acceptables)))

(defn- negotiate-charset*
  "Returns a pair. The first is the charset alias used in the Accept
  header by the user-agent, the second is the charset alias declared by
  the server. Often these are the same, but if they differ, use the
  first alias when talking with the user-agent, while using the second
  alias while asking the resource/service to encode the representation"
  [acceptables candidates]
  (let [winner
        (->> candidates
             (keep (partial any-charset-acceptable? acceptables))
             (sort-by #(vec (map :weight %)))
             reverse ;; highest weight wins
             first ;; winning pair
             )]
    (when winner
      (let [cs1 (-> winner first cs/charset)
            cs2 (-> winner second cs/charset)]
        (if (not= cs1 "*")
          ;; We return a pair. The first is what we set the charset
          ;; parameter of the Content-Type header. The second is what we
          ;; ask the server to provide. These could be different, because
          ;; the user-agent and server may be using different aliases for
          ;; the same charset.
          [cs1 cs2]
          ;; Otherwise, the server gets to dictate the charset
          [cs2 cs2]
          )))))

(defn negotiate-charset [accept-charset-header candidates]
  (negotiate-charset*
   (when accept-charset-header
     (map cs/to-charset-map (map str/trim (str/split accept-charset-header #"\s*,\s*"))))
   (map cs/to-charset-map candidates)))

(defn parse-encoding [s]
  (let [[_ encoding q]
        (re-matches (re-pattern (str "("  "(?:" http-token "|\\*)" ")(?:(?:;q=)(" http-token "))?"))
                    s)]
    {:encoding encoding
     :weight (if q (try
                     (Float/parseFloat q)
                     (catch java.lang.NumberFormatException e
                       1.0))
                 1.0)}))

(defn negotiate-encoding [accept-encoding-header candidates]
  (when accept-encoding-header
    (let [acceptable-encodings (map parse-encoding (map str/trim (str/split accept-encoding-header #"\s*,\s*")))]
      ;; TODO
      )))

(defn negotiate-language [accept-language-header candidates]
  (infof "candidates: %s" candidates)
  (infof "accept-language-header: %s" accept-language-header)
  (let [res (when candidates
              (java.util.Locale/lookupTag
               (or
                (some-> accept-language-header java.util.Locale$LanguageRange/parse)
                (java.util.Locale$LanguageRange/parse (str/join "," candidates)))
               (seq candidates)))
        ]
    (infof "res is %s" res)
    res))


;; ------------------------------------------------------------------------
;; Unified negotiation

(s/defschema RequestInfo
  {:method s/Keyword
   (s/optional-key :accept) s/Str       ; Accept header value
   (s/optional-key :accept-charset) s/Str ; Accept-Charset header value
   (s/optional-key :accept-encoding) s/Str ; Accept-Encoding header value
   (s/optional-key :accept-language) s/Str ; Accept-Language header value
   })

(s/defschema NegotiationResult
  {:method s/Keyword
   ;; There is a subtle distinction between a missing entry and a nil
   ;; entry.  If content-type/charset is nil, it means no acceptable
   ;; content-type, whereas if the content-type/charset entry is missing
   ;; it means no content-type/charset is required (no resource
   ;; representation). These differences affect whether a 406 status is
   ;; returned.
   (s/optional-key :content-type) (s/maybe {:type s/Str
                                            :subtype s/Str
                                            :parameters {s/Str s/Str}
                                            :weight s/Num})
   (s/optional-key :charset) (s/maybe (s/pair s/Str "known-by-client" s/Str "known-by-server"))
   (s/optional-key :encoding) (s/maybe s/Str)
   (s/optional-key :language) (s/maybe s/Str)
   :request RequestInfo})

(s/defschema ServerAcceptable
  {(s/optional-key :method) #{s/Keyword}
   (s/optional-key :content-type) #{MediaTypeMap}
   (s/optional-key :charset) #{CharsetMap}
   (s/optional-key :encoding) #{s/Str}
   (s/optional-key :language) #{s/Str}})

(defn merge-encoding [m accept-encoding encodings]
  (when-let [encoding (negotiate-encoding accept-encoding encodings)]
    (merge m {:encoding encoding})))

(defn merge-language [m accept-language langs]
  (when-let [lang (negotiate-language accept-language langs)]
    (infof "negotiated language is %s" lang)
    (merge m {:language lang})))

(s/defn acceptable?
  [request :- RequestInfo
   server-acceptable :- ServerAcceptable]
  :- NegotiationResult
  ;; If server-acceptable specifies a set of methods, find a
  ;; match, otherwise match on the request method so that
  ;; server-acceptable method guards are strictly optional.
  (when-let [method ((or (:method server-acceptable) identity) (:method request))]
    (cond->
        (when-let [cts (:content-type server-acceptable)]
          (let [content-type (negotiate-content-type (or (:accept request) "*/*") cts)]
            (merge
             {:content-type content-type}
             (when content-type {:charset (negotiate-charset (:accept-charset request) (:charset server-acceptable))})
             )))
      true (merge {:method method :request request})
      (:accept-encoding request) (merge-encoding (:accept-encoding request) (:encoding server-acceptable))
      true (merge-language (:accept-language request) (:language server-acceptable))
      )))

(s/defn negotiate
  "Return a sequence of negotiation results, ordered by
  preference (client first, then server). The request and each
  server-acceptable is presumed to have been pre-validated."
  [request :- RequestInfo
   server-acceptables :- #{ServerAcceptable}]
  :- [NegotiationResult]
  (infof "negotiate: request is %s, server-acceptables is %s" request server-acceptables)
  (->> server-acceptables
       (keep (partial acceptable? request))
       (sort-by (juxt (comp :weight :content-type) (comp :charset)
                      ;; TODO: Add encoding and language
                      ) (comp - compare))))

(defn add-charset? [mt]
  (and (= (:type mt) "text")
       ;; See http://tools.ietf.org/html/rfc6657 TODO: This list is not
       ;; very comprehensive, go through 'text/*' IANA registrations
       ;; http://www.iana.org/assignments/media-types/media-types.xhtml
       (not (contains? #{"text/html"
                         "text/xml"} (mime/media-type mt)))))

(s/defn vary [method :- s/Keyword
              server-acceptables :- [ServerAcceptable]]
  (let [server-acceptables (filter #((or (:method %) identity) method) server-acceptables)
        varies (remove nil?
                       [(when-let [ct (apply set/union (map :content-type server-acceptables))]
                          (when (> (count ct) 1) :content-type))
                        (when-let [cs (apply set/union (map :charset server-acceptables))]
                          (when (> (count cs) 1) :charset))
                        (when-let [encodings (apply set/union (map :encoding server-acceptables))]
                          (when (> (count encodings) 1) :encoding))
                        (when-let [languages (apply set/union (map :language server-acceptables))]
                          (when (> (count languages) 1) :language))])]
    (when (not-empty varies)
      (set varies))))

(s/defn interpret-negotiation
  "Take a negotiated result and determine status code and message. If
  unacceptable (to the client) content-types yield 406. Unacceptable (to
  the server) content-types yield 415- Unsupported Media Type"
  ;; TODO: Result should not be s/maybe
  [{:keys [method content-type charset encoding language request] :as result} :- (s/maybe NegotiationResult)]
  :- {(s/optional-key :status) s/Int
      (s/optional-key :message) s/Str
      (s/optional-key :content-type) MediaTypeMap
      (s/optional-key :client-charset) s/Str
      (s/optional-key :server-charset) s/Str
      (s/optional-key :encoding) s/Str
      (s/optional-key :language) s/Str}

  (infof "interpreting neg: result is %s" result)
  (cond
    (and (contains? result :content-type)
         (nil? content-type))
    {:status 406 :message "Not Acceptable (content-type)"}

    (and (:accept-charset request)
         (contains? result :charset)
         (nil? charset))
    {:status 406 :message "Not Acceptable (charset)"}

    ;; Note: We don't send a 406 if there is no negotiated encoding,
    ;; instead we use the 'identity' encoding, as per the spec.

    (and (:accept-language request)
         (contains? result :language)
         (nil? language))
    {:status 406 :message "Not Acceptable (language)"}

    :otherwise
    (merge
     {}
     (when content-type
       {:content-type
        (if (and charset
                 (add-charset? content-type)
                 (not (some-> content-type :parameters (get "charset"))))
          (assoc-in content-type [:parameters "charset"] (first charset))
          content-type)})
     ;; Charsets can be known by aliases, so each party
     ;; is given the exact name that they have
     ;; specified the charset in, rather than the
     ;; canonical name of the charset. A party should
     ;; use the canonical name if possible, but if it
     ;; doesn't, there might be a valid reason why not
     ;; and we should honor its decision.
     (when charset {:client-charset (first charset)
                    :server-charset (second charset)})
     ;; It's true that the spec. says to default to
     ;; 'identity', but this is really the same as not
     ;; negotiating an encoding at all
     (when encoding {:encoding encoding})
     (when language {:language language}))))


(s/defn extract-request-info [req] :- RequestInfo
  (merge {:method (:request-method req)}
         (when-let [header (get-in req [:headers "accept"])]
           {:accept header})
         (when-let [header (get-in req [:headers "accept-charset"])]
           {:accept-charset header})
         (when-let [header (get-in req [:headers "accept-encoding"])]
           {:accept-encoding header})
         (when-let [header (get-in req [:headers "accept-language"])]
           {:accept-language header})))

;; TODO: Should also pre-parsed specifications: {:method :get :content-type MimeTypeMap}
(defprotocol SetCoercion
  (to-set [_] "Coerce to a set, useful for a shorthand when specifying
  representation entries, which must always be coerced to sets."))

(extend-protocol SetCoercion
  java.util.Set
  (to-set [s] s)
  clojure.lang.Sequential
  (to-set [s] (set s))
  Object
  (to-set [o] #{o})
  nil
  (to-set [_] nil))

(defprotocol ListCoercion
  (to-list [_] "Coerce to a list, useful for a shorthand when specifying
  representation entries where ordering is relevant (languages)"))

(extend-protocol ListCoercion
  clojure.lang.Sequential
  (to-list [s] s)
  Object
  (to-list [o] [o])
  nil
  (to-list [_] nil))

(defn parse-representations
  "For performance reasons it is sensible to parse the representations ahead of time, rather than on each request. mapv this function onto the result of representations"
  [reps]
  (when reps
    (mapv
     (fn [rep]
       (merge
        (select-keys rep [:method])
        (when-let [ct (:content-type rep)]
          {:content-type (set (map mime/string->media-type (to-set ct)))})
        (when-let [cs (:charset rep)]
          {:charset (set (map cs/to-charset-map (to-set cs)))})
        (when-let [enc (:encoding rep)]
          {:encoding (to-set enc)})
        (when-let [langs (:language rep)]
          {:language (to-list langs)})))
     reps)))

(defn to-vary-header [vary]
  (str/join ", "
            (filter string? (map {:charset "accept-charset"
                                  :content-type "accept"
                                  :encoding "accept-encoding"
                                  :language "accept-language"}
                                 vary))))


;; TODO: see rfc7231.html#section-3.4.1

;; "including both the explicit
;; negotiation fields of Section 5.3 and implicit characteristics, such
;; as the client's network address or parts of the User-Agent field."

;; selection of representation can be made based on the User-Agent header, IP address, other 'implicit' data in the request, etc., so this needs to be extensible and overrideable

;; "In order to improve the server's guess, a user agent MAY send request header fields that describe its preferences."

;;    "A Vary header field (Section 7.1.4) is often sent in a response
;;    subject to proactive negotiation to indicate what parts of the
;;    request information were used in the selection algorithm."


;; TODO Should also allow re-negotiation for errors, and allow a special type
;; of representations that declares its just for errors, so users can say
;; they can provide content in both text/html and application/csv but
;; errors must be in text/plain.

;; TODO A capability that doesn't supply a method guard /should/ mean ALL methods.
