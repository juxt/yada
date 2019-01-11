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


(deftest token-test
  (is (nil? (re-matches (re-pattern syn/token) "B€sic") ))
  (is (= "Basic" (re-matches (re-pattern syn/token) "Basic"))))


;; TODO: Test token68-lookahead

(syn/parse-credentials "Basic seflijasef==,bar zip=barf")

(deftest parse-credentials-test
  (are [input expected] (= expected (syn/parse-credentials input))

    "Bearer"
    [#:yada.syntax{:type :yada.syntax/credentials, :auth-scheme "bearer"}]

    "Basic seflijasef=="
    [#:yada.syntax{:type :yada.syntax/credentials,
	           :auth-scheme "basic",
	           :value "seflijasef==",
	           :value-type :yada.syntax/token68}]


    ;; Multiple schemes
    "Basic seflijasef==,bar zip=barf"
    [#:yada.syntax{:type :yada.syntax/credentials,
	                 :auth-scheme "basic",
	                 :value "seflijasef==",
	                 :value-type :yada.syntax/token68}
	   #:yada.syntax{:type :yada.syntax/credentials,
	                 :auth-scheme "bar",
	                 :value
	                 [#:yada.syntax{:type :yada.syntax/auth-param,
	                                :name "zip",
	                                :value "barf",
	                                :value-type :yada.syntax/token}],
	                 :value-type :yada.syntax/auth-param-list}]

    ;; Complex params
    "Digest foo=abc,bar =\"def\",a=b,cd=cd   ,z=y"
    [#:yada.syntax{:type :yada.syntax/credentials,
	                 :auth-scheme "digest",
	                 :value
	           [#:yada.syntax{:type :yada.syntax/auth-param,
	                          :name "foo",
	                          :value "abc",
	                          :value-type :yada.syntax/token}
	            #:yada.syntax{:type :yada.syntax/auth-param,
	                          :name "bar",
	                          :value "def",
	                          :value-type :yada.syntax/quoted-string}
	            #:yada.syntax{:type :yada.syntax/auth-param,
	                          :name "a",
	                          :value "b",
	                          :value-type :yada.syntax/token}
	            #:yada.syntax{:type :yada.syntax/auth-param,
	                          :name "cd",
	                          :value "cd",
	                          :value-type :yada.syntax/token}
	            #:yada.syntax{:type :yada.syntax/auth-param,
	                          :name "z",
	                          :value "y",
	                          :value-type :yada.syntax/token}],
	                 :value-type :yada.syntax/auth-param-list}]


    "foo,bar"
    [#:yada.syntax{:type :yada.syntax/credentials, :auth-scheme "foo"}
     #:yada.syntax{:type :yada.syntax/credentials, :auth-scheme "bar"}]))
