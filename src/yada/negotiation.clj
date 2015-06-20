(ns yada.negotiation
  (:require
   [yada.mime :as mime]
   [yada.charset :as cs]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.tools.trace :refer :all]
   [clojure.string :as str])
  (:import [yada.charset CharsetMap]))

;; ------------------------------------------------------------------------
;; Content types

(defn- acceptable?
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

(defn- any-acceptable? [acceptables candidate]
  (some #(acceptable? % candidate) acceptables))

(defn- negotiate-content-type*
  "Return the best content type via negotiation."
  [acceptables candidates]
  (->> candidates
       (keep (partial any-acceptable? acceptables))
       (sort-by #(vec (map :weight %)))
       reverse ;; highest weight wins
       first ;; winning pair
       second ;; extract the server provided mime-type
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
              (cs/canonical-name candidate)))
          )
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
          ))
      )))

(defn negotiate-charset [accept-charset-header candidates]
  (negotiate-charset*
   (when accept-charset-header
     (map cs/to-charset-map (map str/trim (str/split accept-charset-header #"\s*,\s*"))))
   (map cs/to-charset-map candidates)))
