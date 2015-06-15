(ns yada.negotiation-test
  (:require [yada.negotiation :refer :all]
            [yada.mime :as mime]
            [clojure.test :refer :all]
            [yada.test.util :refer [given]]
            ))

(deftest conneg-test
  (is (= (print-str (mime/string->media-type "text/html;level=1.0")) "text/html;level=1.0;q=1.0"))

  (are [accept candidates => expected] (= (negotiate-content-type accept candidates) (mime/string->media-type expected))
    "text/*" ["text/html"] => "text/html"
    "text/*" ["image/png" "text/html"] => "text/html"

    "text/*;q=0.3, text/html;q=0.7, text/html;level=1, text/html;level=2;q=0.4, */*;q=0.5  " ["text/html;level=1" "text/html"] => "text/html;level=1"))
