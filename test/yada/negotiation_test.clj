(ns yada.negotiation-test
  (:require [yada.negotiation :refer :all]
            [yada.mime :as mime]
            [clojure.test :refer :all]
            [yada.test.util :refer [given]]
            ))

(deftest content-type-test
  (is (= (print-str (mime/string->media-type "text/html;level=1.0")) "text/html;q=1.0;level=1.0"))

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
