(ns yada.syntax-test
  (:require
   [yada.syntax :as syn]
   [clojure.test :refer :all]))

(deftest sanity-test
  ;; The alphabet, upper and lower case
  (is (= (* 26 2) (count syn/ALPHA)))
  (is (= 10 (count syn/DIGIT)))
  (is (= 16 (count syn/HEXDIG))))

(deftest expansion-test
  (are [expected terminal] (= expected (syn/expand-with-character-classes terminal))
    "\\p{Blank}" syn/WSP
    "\\p{Alnum}\\x60\\x21\\x23\\x24\\x25\\x26\\x27\\x2a\\x2b\\x2d\\x2e\\x7c\\x5e\\x7e\\x5f" syn/tchar))
