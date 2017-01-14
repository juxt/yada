;; Copyright © 2014-2017, JUXT LTD.

(ns ^{:doc "Test utilities"}
  phonebook.util
  (:require
   [byte-streams :as bs]))

(defn to-string [s]
  (bs/convert s String))
