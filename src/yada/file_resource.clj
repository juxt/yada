;; Copyright © 2015, JUXT LTD.

(ns yada.file-resource
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all]
            [hiccup.core :refer (html h)]
            [ring.util.mime-type :as mime]
            [ring.util.response :refer (redirect)]
            [yada.representation :refer (full-type)]
            [yada.resource :refer [Resource ResourceConstructor]])
  (:import [java.io File]
           [java.util Date]))

(defn legal-name [s]
  (and
   (not= s "..")
   (re-matches #"[\w\.]+" s)))

(defn- child-file [dir name]
  (assert (.startsWith name "/"))
  (let [name (.substring name 1)] ; remove leading /
    (when-not (legal-name name)
      (warn "Attempt to make a child file which ascends a directory")
      (throw (ex-info "TODO"
                      {:status 400
                       :body (format "Attempt to make a child file which ascends a directory, name is '%s'" name)
                       ;;:yada.core/http-response true
                       })))
    (io/file dir name)))

(defn dir-index [dir content-type]
  (case content-type
    "text/plain"
    (apply str
           (for [child (sort (.list dir))]
             (str child \newline)
             ))
    "text/html"
    (html
     [:html
      [:head
       [:title (.getName dir)]]
      [:body
       (for [child (sort (.list dir))]
         [:p [:a {:href child} child]])]])))

(extend-protocol Resource
  File
  (fetch [this ctx]
    ;; Since File is both the resource and its proxy
    this)
  (exists? [f ctx] (.exists f))
  (last-modified [f ctx] (Date. (.lastModified f)))
  (produces [f ctx]
    (if (.isFile f)
      [(mime/ext-mime-type (.getName f))]
      (when-let [path-info (-> ctx :request :path-info)]
        (if (= path-info "/")
          ;; We can deliver directory contents in numerous types
          ["text/html" "text/plain"]
          (let [child (child-file f path-info)]
            (when (.isFile child)
              [(mime/ext-mime-type (.getName child))]))))))

  (content-length [f ctx]
    (when (.isFile f)
      (.length f)))

  ;; When specifying :state as a file, the resource-map should also
  ;; specify whether the file's content should be encoded to a given
  ;; charset, or inherit the charset of the request's body
  ;; representation.

  (get-state [f content-type ctx]
    ;; TODO: Check that file is compliant with the given content-type
    ;; (type/subtype and charset)

    (if-let [path-info (-> ctx :request :path-info)]
      (cond
        (and (.isDirectory f) path-info)
        (if (= path-info "/")
          ;; TODO: The content-type indicates the format. Use support in
          ;; yada.representation to help format the response body.
          ;; This will have to do for now
          (dir-index f content-type)

          (let [f (child-file f path-info)]
            (cond
              (.isFile f) f
              (.isDirectory f) (dir-index f content-type)
              :otherwise (throw (ex-info "File not found" {:status 404 :yada.core/http-response true})))))

        (.isDirectory f)
        (throw (ex-info "" {:status 302 :headers {"location" (str (-> ctx :request :uri) "/")}
                            :yada.core/http-response true}))
        )

      ;; Redirect so that path-info is not nil
      (if (.isDirectory f)
        (throw (ex-info "" {:status 302 :headers {"location" (str (-> ctx :request :uri) "/")}
                            :yada.core/http-response true}))
        ;; Otherwise we're a normal file with no path-info. Let's return it.
        f
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
        (throw (ex-info "TODO: Directory creation from archive stream is not yet implemented" {:path-info path-info
                                                                                               :dir? (.isDirectory f)}))

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
            (if (seq child-files)
              (throw (ex-info "By default, the policy is not to delete a non-empty directory" {}))
              (io/delete-file f)
              )))))))

(extend-protocol ResourceConstructor
  File
  (make-resource [f]
    ;; TODO: Create different records based on .isFile or .isDirectory
    f
    )
  )
