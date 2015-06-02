;; Copyright © 2015, JUXT LTD.

(ns yada.test.util-test
  (:require
   [yada.test.util :refer :all]
   [clojure.test :refer :all]
   [schema.core :as s]))

(deftest myself
  (given {:foo "foo" :bar "bar"}
    identity := {:foo "foo" :bar "bar"}
    identity :- {:foo s/Str :bar s/Str}
    :foo := "foo"
    :foo :!= "bar"
    :foo :? string?
    count := 2
    count :!= 3
    [:foo count] := (count "foo")
    [:foo count] :!= 10
    :foo :# "fo+"
    :foo :!# "fo+d"
    identity :> {:foo "foo"}
    identity :< {:foo "foo" :bar "bar" :zip "zip"}
    )
  (given [1 2 3]
    first := 1
    identity :- [s/Num]
    identity :<  [1 2 3 4]
    identity :!<  [1 3 4]
    identity :> [1 2]
    identity :!> [1 4]
    count := 3
    count :- s/Num
    )
  )
