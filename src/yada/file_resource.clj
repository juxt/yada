;; Copyright © 2015, JUXT LTD.

(ns yada.file-resource
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all]
            [hiccup.core :refer (html h)]
            [ring.util.mime-type :refer (ext-mime-type)]
            [ring.util.response :refer (redirect)]
            [ring.util.time :refer (format-date)]
            [yada.resource :refer [Resource ResourceRepresentations ResourceFetch ResourceConstructor]]
            [yada.mime :as mime])
  (:import [java.io File]
           [java.util Date TimeZone]
           [java.text SimpleDateFormat]
           [java.nio.charset Charset]))


;; TODO: Fix this to ensure that ascending a directory is completely
;; impossible, and test.
(defn legal-name [s]
  (and
   (not= s "..")
   (re-matches #"[^/]+(?:/[^/]+)*/?" s)))

(defn- child-file [dir name]
  (when-not (legal-name name)
    (warn "Attempt to make a child file which ascends a directory")
    (throw (ex-info "TODO"
                    {:status 400
                     :body (format "Attempt to make a child file which ascends a directory, name is '%s'" name)
                     ;;:yada.core/http-response true
                     })))
  (io/file dir name))

(defn dir-index [dir content-type]
  (case (mime/media-type content-type)
    "text/plain"
    (apply str
           (for [child (sort (.listFiles dir))]
             (str (.getName child) \newline)
             ))
    "text/html"
    (html
     [:html
      [:head
       [:title (.getName dir)]]
      [:body
       [:table
        [:thead
         [:tr
          [:th "Name"]
          [:th "Size"]
          [:th "Last modified"]]]
        [:tbody
         (for [child (sort (.listFiles dir))]
           [:tr
            [:td [:a {:href (if (.isDirectory child) (str (.getName child) "/") (.getName child))} (.getName child)]]
            [:td (if (.isDirectory child) "" (.length child))]
            [:td (.format
                  (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss zzz")
                    (.setTimeZone (TimeZone/getTimeZone "UTC")))
                  (java.util.Date. (.lastModified child)))]])]]]])))

(defrecord FileResource [f]
  ResourceFetch
  (fetch [this ctx] this)

  Resource
  (exists? [_ ctx] (.exists f))
  (last-modified [_ ctx] (Date. (.lastModified f)))
  (get-state [_ content-type ctx] f)
  (put-state! [_ content content-type ctx]
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
    (bs/transfer (-> ctx :request :body) f))

  (delete-state! [_ ctx]
    (.delete f))

  ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
      :content-type #{(ext-mime-type (.getName f))}}
     {:method #{:put}}
     {:method #{:delete}}])
  )

#_(defrecord DirectoryResource [dir]
  ResourceFetch
  (fetch [this ctx] this)

  Resource
  #_(supported-methods [_ ctx] #{:get :head :put :delete})
  (exists? [_ ctx] (.exists dir))
  (last-modified [_ ctx] (Date. (.lastModified dir)))
  (produces [_ ctx]
    (when-let [path-info (-> ctx :request :path-info)]
      (if (or (= path-info "") (.endsWith path-info "/"))
        ;; We can deliver directory contents in numerous types
        ["text/html" "text/plain"]
        ;; Otherwise, it's the child-file
        (let [child (child-file dir path-info)]
          (when (.isFile child)
            [(ext-mime-type (.getName child))])))))
  (produces-charsets [_ ctx]
    (when-let [path-info (-> ctx :request :path-info)]
      (when (or (= path-info "") (.endsWith path-info "/"))
        ["UTF-8" "US-ASCII;q=0.9"])))
  (get-state [_ content-type ctx]
    (if-let [path-info (-> ctx :request :path-info)]
      (if (= path-info "")
        ;; TODO: The content-type indicates the format. Use support in
        ;; yada.representation to help format the response body.
        ;; This will have to do for now
        (dir-index dir content-type)

        (let [f (child-file dir path-info)]
          (cond
            (.isFile f) f
            (.isDirectory f) (dir-index f content-type)
            :otherwise (throw (ex-info "File not found" {:status 404 :yada.core/http-response true})))))

      ;; Redirect so that path-info is not nil - there is a case for this being done in the bidi handler
      (throw (ex-info "" {:status 302 :headers {"location" (str (-> ctx :request :uri) "/")}
                          :yada.core/http-response true}))))
  (put-state! [_ content content-type ctx]
    (if-let [path-info (-> ctx :request :path-info)]
      (let [f (child-file dir path-info)]
        (bs/transfer (-> ctx :request :body) f))
      (throw (ex-info "TODO: Directory creation from archive stream is not yet implemented" {}))))

  (delete-state! [_ ctx]
    (let [path-info (-> ctx :request :path-info)]
      ;; TODO: We must be ensure that the path-info points to a file
      ;; within the directory tree, otherwise this is an attack vector -
      ;; we should return 403 in this case - same above with PUTs and POSTs

      (if (= path-info "")
        (if (seq (.listFiles dir))
          (throw (ex-info "By default, the policy is not to delete a non-empty directory" {:files (.listFiles dir)}))
          (io/delete-file dir)
          )

        (let [f (child-file dir path-info)]
          ;; f is not certain to exist
          (if (.exists f)
            (.delete f)
            (throw (ex-info {:status 404 :yada.core/http-response true}))))

        ))))

(extend-protocol ResourceConstructor
  File
  (make-resource [f]
    (if (.isDirectory f)
      #_(->DirectoryResource f)
      (throw (ex-info "TODO: Directory resource" {}))
      (->FileResource f))))
