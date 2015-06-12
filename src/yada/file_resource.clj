;; Copyright © 2015, JUXT LTD.

(ns yada.file-resource
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all]
            [ring.util.mime-type :as mime]
            [yada.resource :refer [Resource]])
  (:import [java.io File]
           [java.util Date]))

(defn legal-name [s]
  (and
   (not= s "..")
   (re-matches #"[\w\.]+" s)))

(defn- child-file [dir name]
  (when-not (legal-name name)
    (warn "Attempt to make a child file which ascends a directory")
    (throw (ex-info "TODO" {:status 400
                            :yada.core/http-response true})))
  (io/file dir name))

(extend-protocol Resource
  File
  (exists? [f] (.exists f))
  (last-modified [f] (Date. (.lastModified f)))
  (produces [f]
    (cond
      (.isFile f) [(mime/ext-mime-type (.getName f))]
      (.isDirectory f) ["text/html" "text/plain"]))
  (content-length [f]
    (when (.isFile f)
      (.length f)))

  ;; When specifying :state as a file, the resource-map should also
  ;; specify whether the file's content should be encoded to a given
  ;; charset, or inherit the charset of the request's body
  ;; representation.

  (get-state [f content-type ctx]
    ;; TODO: Check that file is compliant with the given content-type
    ;; (type/subtype and charset)

    (let [path-info (-> ctx :request :path-info)]
      (cond
        (and (nil? path-info) (.isFile f)) f

        (and (.isDirectory f) path-info) (let [f (child-file f path-info)]
                                           (when (.exists f) f))

        ;; TODO: The content-type indicates the format. Use support in
        ;; yada.representation to help format the response body.
        ;; This will have to do for now
        (.isDirectory f) (str/join "\n" (sort (.list f)))
        )))

  (put-state! [f content content-type ctx]
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
        (let [f (child-file f path-info)]
          (bs/transfer (-> ctx :request :body) f))

        (.isDirectory f)
        (throw (ex-info "TODO: Directory creation from archive stream is not yet implemented" {}))

        :otherwise
        (bs/transfer (-> ctx :request :body) f))))

  (delete-state! [f ctx]
    (let [path-info (-> ctx :request :path-info)]
      ;; TODO: We must be ensure that the path-info points to a file
      ;; within the directory tree, otherwise this is an attack vector -
      ;; we should return 403 in this case - same above with PUTs and POSTs
      (if (.isFile f)
        (if path-info
          (throw (ex-info {:status 404 :yada.core/http-response true}))
          (.delete f))

        ;; Else directory
        (if path-info
          (let [f (child-file f path-info)]
            ;; f is not certain to exist
            (if (.exists f)
              (.delete f)
              (throw (ex-info {:status 404 :yada.core/http-response true}))))

          (let [child-files (.listFiles f)]
            (if (empty? child-files)
              (io/delete-file f)
              (throw (ex-info "By default, the policy is not to delete a non-empty directory" {}))))))))





  )
