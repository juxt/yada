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
  (insert! [_ ctx] "Insert a new sub-resource")
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
    ;; The reason to use bs/transfer is to allow an efficient copy of byte buffers
    ;; should the following be true:

    ;; 1. The web server is aleph
    ;; 2. Aleph has been started with the raw-stream? option set to true

    ;; In which case, byte-streams will efficiently copy the Java NIO
    ;; byte buffers to the file without streaming.

    ;; However, if the body is a 'plain old' java.io.InputStream, the
    ;; file will still be written In summary, the best of both worlds.

    ;; The analog of this is the ability to return a java.io.File as the
    ;; response body and have aleph efficiently stream it via NIO. This
    ;; code allows the same efficiency for file uploads.

    (let [path-info (-> ctx :request :path-info)]
      (cond
        (and (.isDirectory f) path-info)
        (let [f (io/file f path-info)]
          (bs/transfer (-> ctx :request :body) f))

        (.isFile f)
        (bs/transfer (-> ctx :request :body) f)

        (.isDirectory f)
        (throw (ex-info "TODO: Directory creation from archive stream is not yet implemented" {})))))

  (delete! [f ctx]
    (let [path-info (-> ctx :request :path-info)]
      (if (.isFile f)
        (if path-info
          (throw (ex-info {:status 404 :yada.core/http-response true}))
          (.delete f))

        ;; Else directory
        (if path-info
          (let [f (io/file f path-info)]
            ;; f is not certain to exist
            (if (.exists f)
              (.delete f)
              (throw (ex-info {:status 404 :yada.core/http-response true}))))

          (let [child-files (.listFiles f)]
            (if (empty? child-files)
              (io/delete-file f)
              (throw (ex-info "By default, the policy is not to delete a non-empty directory" {}))))))))

  Date
  (last-modified [d] d)

  nil
  ;; last-modified of 'nil' means we don't consider last-modified
  (last-modified [_] nil)

  Object
  (last-modified [_] nil)

  )
