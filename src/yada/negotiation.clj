(ns yada.negotiation
  (:require
   [yada.mime :as mime]
   [yada.charset :as cs]
   [yada.util :refer [parameters parameter weight]]
   [clojure.tools.logging :refer :all :exclude [trace]]
   [clojure.string :as str]))

;; ------------------------------------------------------------------------
;; Content types

(defn- acceptable? [acceptable candidate]
  (when
      (and
       (or
        (and (= (mime/type acceptable) (mime/type candidate))
             (= (mime/subtype acceptable) (mime/subtype candidate)))

        (and (= (mime/type acceptable) (mime/type candidate))
             (= (mime/subtype acceptable) "*"))

        (and (= (mime/full-type acceptable) "*/*")))
       (= (parameters acceptable) (parameters candidate))
       )
    [acceptable candidate]))

(defn- any-acceptable? [acceptables candidate]
  (some #(acceptable? % candidate) acceptables))

(defn- negotiate-content-type*
  "Return the best content type via negotiation."
  [acceptables candidates]
  (->> candidates
       (keep (partial any-acceptable? acceptables))
       (sort-by #(vec (map weight %)))
       reverse ;; highest weight wins
       first ;; winning pair
       second ;; extract the server provided mime-type
       ))

(defn negotiate-content-type [accept-header available]
  (negotiate-content-type*
   (map mime/to-media-type-map (map str/trim (str/split accept-header #"\s*,\s*")))
   (map mime/to-media-type-map available)))

;; ------------------------------------------------------------------------
;; Charsets

(defn- acceptable-charset? [acceptable-charset candidate]
  (when
      (or (= (cs/charset acceptable-charset) "*")
          (= (cs/canonical-name acceptable-charset)
             (cs/canonical-name candidate)))
    [acceptable-charset candidate]))

(defn any-charset-acceptable? [acceptables candidate]
  (if (nil? acceptables)
    [candidate candidate] ; no header means the user-agent accepts 'any charset' in response - rfc7231.html#section-5.3.3
    (some #(acceptable-charset? % candidate) acceptables)))

(defn negotiate-charset*
  "Returns a pair."
  [acceptables candidates]
  (let [winner
        (->> candidates
             (keep (partial any-charset-acceptable? acceptables))
             (sort-by #(vec (map weight %)))
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
