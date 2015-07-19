;; Copyright © 2015, JUXT LTD.

(ns yada.negotiation-test
  (:require [clojure.test :refer :all]
            [yada.negotiation :refer :all]
            [yada.mime :as mime]
            [yada.charset :as charset]
            [juxt.iota :refer [given]]
            [schema.core :as s]
            [schema.test :as st])
  (:import [yada.mime MediaTypeMap]))

(deftest content-type-test
  (is (= (print-str (mime/string->media-type "text/html;level=1.0")) "#yada.media-type[text/html;q=1.0;level=1.0]"))

  (are [accept candidates => expected] (= (negotiate-content-type accept (map mime/string->media-type candidates)) (dissoc (mime/string->media-type expected) :weight))
    "text/*" ["text/html"] => "text/html"
    "text/*" ["image/png" "text/html"] => "text/html"
    "image/*,text/*" ["image/png;q=0.8" "text/jpeg;q=0.9"] => "text/jpeg"
    "text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4, */*;q=0.5  " ["text/html;level=1" "text/html"] => "text/html;level=1"
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    ["text/html;charset=utf-8"] => "text/html;charset=utf-8"
    ))

;;    The special value "*", if present in the Accept-Charset field,
;;    matches every charset that is not mentioned elsewhere in the
;;    Accept-Charset field.  If no "*" is present in an Accept-Charset
;;    field, then any charsets not explicitly mentioned in the field are
;; considered "not acceptable" to the client.

;; A request without any Accept-Charset header field implies that the
;;    user agent will accept any charset in response.  Most general-purpose
;; user agents do not send Accept-Charset, unless specifically

(deftest charset-test
  (are [accept-charset-header available expected]
      (= (negotiate-charset accept-charset-header available) expected)
    "*" ["utf-8" "Shift_JIS;q=0.3"] ["utf-8" "utf-8"]
    "*" ["utf-8;q=0.8" "Shift_JIS;q=1.0"] ["Shift_JIS" "Shift_JIS"]
    "utf-8" ["unicode-1-1"] nil
    "utf-8" ["utf-8;q=0.8" "Shift_JIS;q=1.0"] ["utf-8" "utf-8"]
    "unicode-1.1" ["utf-8;q=0.8" "Shift_JIS;q=1.0"] nil
    "unicode-1.1" ["utf-8;q=0.8" "Shift_JIS;q=1.0" "unicode-1.1;q=0.1"] ["unicode-1.1" "unicode-1.1"]
    nil ["utf-8;q=0.8" "Shift_JIS;q=1.0"] ["Shift_JIS" "Shift_JIS"]
    nil ["utf-8;q=0.8" "Shift_JIS;q=0.2"] ["utf-8" "utf-8"]
    nil yada.resource/platform-charsets ["UTF-8" "UTF-8"]
    "dummyfox" ["utf-8;q=0.8" "Shift_JIS;q=1.0"] nil
    ))

(st/deftest method-test
  (testing "single option matches"
    (given (first (negotiate {:method :get} [{:method #{:get :head}}]))
      :method := :get
      :content-type := nil
      :charset := nil
      [interpret-negotiation :status] := nil))
  (testing "second option works"
    (given (first (negotiate {:method :post} [{:method #{:get :head}}{:method #{:post :put}}]))
      :method := :post
      :content-type := nil
      :charset := nil
      [interpret-negotiation :status] := nil)))

(st/deftest content-type-test
  (testing "no charset"
    (given (interpret-negotiation
            (first (negotiate {:method :get :accept "text/html"}
                              (parse-representations
                               [{:method #{:get :head}
                                 :content-type #{"text/html"}
                                 }]))))
      :status := nil
      [:content-type :type] := "text"
      [:content-type :subtype] := "html"
      [:content-type :parameters] := {}))

  (testing "charset applied when necessary"
    (given (interpret-negotiation
            (first (negotiate {:method :get :accept "text/plain"}
                              (parse-representations
                               [{:method #{:get :head}
                                 :content-type #{"text/plain"}}

                                {:method #{:get :head}
                                 :content-type #{"text/plain"}
                                 :charset #{"UTF-8"}
                                 }]))))
      :status := nil
      [:content-type :type] := "text"
      [:content-type :subtype] := "plain"
      [:content-type :parameters] := {"charset" "UTF-8"}
      ))

  (testing "charset not applied when it shouldn't be"
    ;; according to http://tools.ietf.org/html/rfc6657
    (given (interpret-negotiation
            (first (negotiate {:method :get :accept "text/html"}
                              (parse-representations
                               [{:method #{:get :head}
                                 :content-type #{"text/html"}}

                                {:method #{:get :head}
                                 :content-type #{"text/html"}
                                 :charset #{"UTF-8"}
                                 }]))))
      :status := nil
      [:content-type :type] := "text"
      [:content-type :subtype] := "html"
      [:content-type :parameters] := {}
      )))

#_(let [rep {:method #{:get :head}
           :content-type #{"text/html"}}]
  (merge
   (select-keys rep [:method])
   (when-let [ct (:content-type rep)]
     {:content-type (set (map mime/string->media-type ct))})
   (when-let [cs (:charset rep)]
     {:content-type (set (map charset/to-charset-map cs))})))

;;   (update-in [:content-type] #(set (map mime/string->media-type %)))
;;   (update-in [:charset] #(set (map charset/to-charset-map %)))

(st/deftest charset-preferred-over-none
  "Ensure that an option with a charset is preferred over an option with
  no charset"
  (is (= (:charset
          (first
           (negotiate {:method :get :accept "text/html"}
                      (parse-representations
                       [{:method #{:get :head}
                         :content-type #{"text/html"}}

                        {:method #{:get :head}
                         :content-type #{"text/html"}
                         :charset #{"utf-8"}
                         }]))))
         ["utf-8" "utf-8"])))

(st/deftest content-type-weight-removed []
  (given (:content-type (interpret-negotiation
                         (first
                          (negotiate {:method :get :accept "text/html"}
                                     (parse-representations
                                      [{:method #{:get}
                                        :content-type #{"text/html;q=0.9"}}
                                       ])))))
    identity :> {:type "text", :subtype "html", :parameters {}}))

(st/deftest method-optional []
  (given (:content-type
          (interpret-negotiation
           (first
            (negotiate {:method :get :accept "text/html"}
                       (parse-representations
                        [{:content-type #{"text/html"}}
                         ])))))
    identity :> {:type "text", :subtype "html", :parameters {}}

    ))

;; TODO: Add accept-language, accept-encoding, content-type & content-encoding

;; TODO: what about charsets on receiving?

;; TODO: Research Transfer-Encoding and TE


;; An example set of server-provided options
#_[{:method #{:get :head}
    :content-type #{"text/html;q=1.0" "text/html"}
    :charset #{"UTF-8" "Shift_JIS;q=0.2"}
    :encoding #{"compress" "gzip" "deflate"}
    :language #{"jp-JP"}
    }
   {:method #{:get :head}
    :content-type #{"text/html;q=1.0"}
    :language #{"en-GB" "en-US;q=0.9"}
    }
   {:method #{:post}
    :accept #{"application/edn"}        ; the server accepts
    :accept-language #{"en-US"}         ; the server accepts
    :content-type #{"application/edn"}  ; the server produces

    ;; rfc7231.html#section-3.1.3.2
    ;; "Content-Language MAY be applied to any media type -- it is not limited to textual documents."
    :language #{"en-GB" "en-US;q=0.9"}


    }]

;; If no charset (usually only if there's an
;; explicit Accept-Charset header sent, or the
;; resource really declares that it provides no
;; charsets), then send a 406, a per the
;; specification.

(deftest no-representations []
  (given (first
          (negotiate
           {:method :put :accept "text/html"}
           [{:method #{:get :head}
             :content-type #{"image/png"}}
            {:method #{:put :delete}}
            ]))
    [identity #(dissoc % :request)] := {:method :put}
    interpret-negotiation := {}
    ))

#_(interpret-negotiation
   (first
    (negotiate
     {:method :get :accept "text/html"}
     [{:method #{:get :head}
       :content-type #{"image/png"}}
      {:method #{:put :delete}}
      ])))


#_(interpret-negotiation
   (first
    (negotiate
     {:method :put :accept "text/html"}
     [{:method #{:get :head}
       :content-type #{"image/png"}}
      {:method #{:put :delete}}
      ])))
