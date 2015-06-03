;; Copyright © 2015, JUXT LTD.

(ns yada.state
  (:require
   [manifold.deferred :as d]
   [clojure.java.io :as io]
   [clojure.tools.logging :refer :all]
   [byte-streams :as bs])
  (import
   [clojure.core.async.impl.protocols ReadPort]
   [java.io File InputStream]
   [java.util Date]))

(defprotocol State
  (exists? [_] "Whether the state actually exists")
  (last-modified [_] "Return the date that the state was last modified.")
  (write! [_ ctx] "Overwrite the state with the given representation. The content-type is the media-type and parameters include :charset. If a deferred is returned, the HTTP response status is set to 202")
  (delete! [_ ctx] "Delete the state. If a deferred is returned, the HTTP response status is set to 202"))

(extend-protocol State
  clojure.lang.Fn
  (last-modified [f]
    (let [res (f)]
      (if (d/deferrable? res)
        (d/chain res #(last-modified %))
        (last-modified res))))
  Number
  (last-modified [l] (Date. l))

  File
  (exists? [f] (.exists f))
  (last-modified [f] (Date. (.lastModified f)))
  (write! [f ctx]
    ;; The idea here is to allow an efficient copy of byte buffers
    ;; should the following be true:

    ;; 1. The web server is aleph
    ;; 2. Aleph has been started with the raw-stream? option set to true

    ;; In which case, byte-streams will copy the Java NIO network byte
    ;; buffers to the file without streaming, hence will be far more
    ;; efficient.

    ;; However, the file will still be written if the body is a 'plain
    ;; old' java.io.InputStream. Hence, the best of both worlds.
    (bs/transfer (-> ctx :request :body) f))

  (delete! [f ctx]
    (when (.exists f)
      (io/delete-file f)))

  Date
  (last-modified [d] d)

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_] nil)

  Object
  (last-modified [_] nil)

  )
