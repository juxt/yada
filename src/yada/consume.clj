;; Copyright © 2014-2017, JUXT LTD.

(ns yada.consume
  (:require
   [byte-streams :as b]
   [manifold.deferred :as d]
   [manifold.stream :as s]))

;; Works ok
#_(defn save-to-file [ctx body-stream f]
  (let [fos (new java.io.FileOutputStream f false)
        fc (.getChannel fos)]
    (d/chain
     (s/reduce
      (fn [ctx buf]

        (infof "count is %s" (:count ctx))
        (let [niobuf (b/to-byte-buffer buf)]
          (infof "Writing single buffer %s" (.capacity niobuf))
          (.write fc niobuf))

        (update ctx :count (fnil inc 0)))
      ctx
      (s/batch 100 100 body-stream))
     (fn [ctx]
       (.close fc)
       (.close fos)
       (assoc-in ctx [:file] f)))))


(defn save-to-file [ctx body-stream ^java.io.File f]
  (let [fos (new java.io.FileOutputStream f false)
        fc (.getChannel fos)]
    (d/chain
     (s/reduce
      (fn [ctx buf]
        #_(infof "count is %s" (:count ctx))
        #_(.retain buf)
        (let [niobuf (b/to-byte-buffer buf)]
          #_(infof "Writing single buffer %s, refCnt is %s" (.capacity niobuf) (.refCnt buf))
          (.write fc niobuf))
        #_(.release buf)

        (update ctx :count (fnil inc 0)))
      ctx
      body-stream)
     (fn [ctx]
       (.close fc)
       (.close fos)
       (assoc-in ctx [:file] f)))))
