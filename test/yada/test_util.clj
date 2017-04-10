;; Copyright © 2014-2017, JUXT LTD.

(ns ^{:doc "Test utilities"}
 yada.test-util
  (:require
   [byte-streams :as bs])
  (:import [ch.qos.logback.classic Logger Level]
           [org.slf4j LoggerFactory]))

(defn etag? [etag]
  (and (string? etag)
       (re-matches #"[0-9a-f]+" etag)))

(defn to-string [s]
  (bs/convert s String))

(defmacro with-level
  "Sets the logging level for ns to level, while executing body."
  ;; See http://stackoverflow.com/questions/3837801/how-to-change-root-logging-level-programmatically
  [level ns & body]
  `(let [root-logger# ^Logger (LoggerFactory/getLogger ~ns)
         old-level# (.getLevel root-logger#)]
     (try
       (.setLevel root-logger# ~level)
       ~@body
       (finally (.setLevel root-logger# old-level#)))))
