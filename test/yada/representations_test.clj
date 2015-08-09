;; Copyright © 2015, JUXT LTD.

(ns yada.representations-test
  (:require
   [clojure.test :refer :all]
   [yada.charset :as charset]
   [yada.mime :as mime]
   [yada.negotiation :as negotiation]))

;; Transducers

(defn acceptable-content-type [accept-content-type]
  (filter (fn [{:keys [content-type]}]
            (#{"*" (:type content-type)} (-> accept-content-type :type)))))

(defn acceptable-content-subtype [accept-content-type]
  (filter (fn [{:keys [content-type]}]
            (#{"*" (:subtype content-type)} (-> accept-content-type :subtype)))))

(defn acceptable-content-type-parameters [accept-content-type]
  (filter (fn [{:keys [content-type]}]
            (= (-> accept-content-type :parameters) (-> content-type :parameters)))))

#_(defn acceptable-charset [accept-charset]
  (filter (fn [{:keys [charset]}]
            (or
             (nil? accept-charset)
             (= "*" accept-charset)
                )

            )))

(defn make-filter
  [acceptance]
  (let [accept-content-type
        (mime/string->media-type
         (or (:content-type acceptance)
             ;; "A request without any Accept header
             ;; field implies that the user agent
             ;; will accept any media type in response."
             ;; -- RFC 7231 Section 5.3.2
             "*/*"))]

    (comp
     (acceptable-content-type accept-content-type)
     (acceptable-content-subtype accept-content-type)
     (acceptable-content-type-parameters accept-content-type)
     #_(acceptable-charset (:charset acceptance)))

    #_(fn [{:keys [content-type charset encoding language]}]
      (and
       ;; Let's compose via transducers for speed rather than a big
       ;; undebugale conjunction.
       (= (-> accept-content-type :parameters) (-> content-type :parameters))
       (#{"*" (:type content-type)} (-> accept-content-type :type))
       (#{"*" (:subtype content-type)} (-> accept-content-type :subtype))
       (or (when-let [accept-charset (:charset acceptance)]
             (or
              (= "*" accept-charset)
              (= (charset/canonical-name (charset/to-charset-map accept-charset))
                 (charset/canonical-name charset))))

           ;; "A request without any Accept-Charset header field implies
           ;; that the user agent will accept any charset in response. "
           ;; -- RFC 7231 Section 5.3.3
           true
           )))))

(defn- acceptable? [acceptance candidate]
  (not-empty (sequence (make-filter acceptance) [candidate])))

(deftest filter-test
  ;; Content-types match
  (is (acceptable? {:content-type "text/html"}
                   {:content-type (mime/string->media-type "text/html")}))

  ;; Content-types don't match
  (is (not (acceptable? {:content-type "text/plain"}
                        {:content-type (mime/string->media-type "text/html")})))

  ;; General wildcard
  (is (acceptable?
       {:content-type "*/*"}
       {:content-type (mime/string->media-type "application/edn")}))

  ;; Sub-type wildcard
  (is (acceptable?
       {:content-type "text/*"}
       {:content-type (mime/string->media-type "text/plain")}))

  ;; Parameters match
  (is (acceptable?
       {:content-type "text/plain;level=1"}
       {:content-type (mime/string->media-type "text/plain;level=1")}))

  ;; Parameters don't match
  (is (not (acceptable?
            {:content-type "text/plain;level=1"}
            {:content-type (mime/string->media-type "text/plain;level=2")})))

  ;; Charsets
  #_(is (acceptable?
       {:charset "utf-8"}
       {:charset (charset/to-charset-map "UTF-8")}))

  )
