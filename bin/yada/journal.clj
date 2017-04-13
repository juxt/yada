;; Copyright © 2015, JUXT LTD.

(ns yada.journal
  (:require
   [bidi.bidi :refer [Matched] :as bidi]))

(def routes ["" [["" :index]
                 [[[bidi/uuid :id]] :entry]]])

(defn path-for [& args]
  (apply bidi/path-for routes args))
