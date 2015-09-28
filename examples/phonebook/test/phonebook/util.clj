;; Copyright © 2015, JUXT LTD.

(ns ^{:doc "Test utilities"}
  phonebook.util
  (:require
   [byte-streams :as bs]))

(defn to-string [s]
  (bs/convert s String))
