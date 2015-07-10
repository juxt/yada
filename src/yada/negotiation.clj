(ns yada.negotiation
  (:require
   [yada.mime :as mime]
   [yada.charset :as cs]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.string :as str]
   [schema.core :as s]
   )
  (:import [yada.charset CharsetMap]))

;; ------------------------------------------------------------------------
;; Content types

(defn- content-type-acceptable?
  "Compare a single acceptable mime-type (extracted from an Accept
  header) and a candidate. If the candidate is acceptable, return a
  sortable vector [acceptable candidate weight1 weight2]. Weight1
  prefers specificity, e.g. prefers text/html over text/* over
  */*. Weight2 gives preference to candidates with a greater number of
  parameters, which preferes text/html;level=1 over text/html. This meets the criteria in the HTTP specifications. Although the preference that should result with multiple parameters is not specified formally, candidates that "
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
          ;; As a stretch, let's see if their canonical names match
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
  "Returns a pair. The first is the charset alias used in the Accept header by the user-agent, the second is the charset alias declared by the server. Often these are the same, but if they differ, use the first alias when talking with the user-agent, while using the second alias while asking the resource/service to encode the representation"
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

(s/defschema NegotiationResult
  {:method s/Keyword
   (s/optional-key :content-type)
   {:type s/Str
    :subtype s/Str
    :parameters {s/Str s/Str}
    :weight s/Num}
   (s/optional-key :charset) (s/pair s/Str "known-by-client" s/Str "known-by-server")})

(s/defn acceptable?
  [request server-acceptable]
  :- NegotiationResult
  (assert (:method server-acceptable) (format "Option must have :method, %s" server-acceptable))
  (let [method ((:method server-acceptable) (:method request))
        content-type (when method
                       (when-let [cts (:content-type server-acceptable)]
                         (negotiate-content-type (or (:accept request) "*/*") (map mime/string->media-type cts))))
        charset (when (and method content-type)
                  (negotiate-charset (:accept-charset request) (:charset server-acceptable)))]
    (when method
      (merge
       {:method method}
       (when content-type {:content-type content-type})
       (when charset {:charset charset})))))

(s/defn negotiate
  "Return a sequence of negotiation results, ordered by
  preference (client first, then server). The request and each
  server-acceptable is presumed to have been pre-validated."
  [request :- {:method s/Keyword
               (s/optional-key :accept) s/Str ; Accept header value
               (s/optional-key :accept-charset) s/Str ; Accept-Charset header value
               }
   server-acceptables :- [{:method #{s/Keyword}
                           (s/optional-key :content-type) #{s/Str}
                           (s/optional-key :charset) #{s/Str}}]]
  :- [NegotiationResult]
  (->> server-acceptables
       (keep (partial acceptable? request))
       (sort-by (juxt (comp :weight :content-type) (comp :charset)) (comp - compare))))

(s/defn interpret-negotiation
  :- {(s/optional-key :status) s/Int
      (s/optional-key :content-type) s/Str
      (s/optional-key :client-charset) s/Str
      (s/optional-key :server-charset) s/Str}
  "Take a negotiated result and determine status code and message. A nil
  result means no methods match, which yields status 405. Otherwise,
  unacceptable (to the client) content-types yield 406. Unacceptable (to
  the server) content-types yield 415- Unsupported Media Type"
  [{:keys [method content-type charset] :as result} :- (s/maybe NegotiationResult)]
  (cond
    (nil? method) {:status 405}
    (nil? content-type) {:status 406}
    :otherwise (merge {}
                      (when content-type
                        {:content-type
                         (mime/media-type->string
                          (if (and charset
                                   ;; Only for text media-types
                                   (= (:type content-type) "text")
                                   ;; But don't overwrite an existing charset
                                   (not (some-> content-type :parameters (get "charset"))))
                            (assoc-in content-type [:parameters "charset"] (first charset))
                            content-type))})
                      (when charset
                        {:client-charset (first charset)
                         :server-charset (second charset)
                         }))))
