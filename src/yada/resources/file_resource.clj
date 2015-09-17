;; Copyright © 2015, JUXT LTD.

(ns yada.resources.file-resource
  (:require
   [byte-streams :as bs]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :refer :all]
   [hiccup.core :refer (html h)]
   [ring.util.mime-type :refer (ext-mime-type)]
   [ring.util.response :refer (redirect)]
   [ring.util.time :refer (format-date)]
   [schema.core :as s]
   [yada.charset :as charset]
   [yada.representation :as rep]
   [yada.resource :refer [Representation RepresentationSets]]
   [yada.protocols :as p]
   [yada.methods :refer (Get GET Put PUT Post POST Delete DELETE)]
   [yada.media-type :as mt])
  (:import [java.io File]
           [java.util Date TimeZone]
           [java.text SimpleDateFormat]
           [java.nio.charset Charset]))

(s/defrecord FileResource [file :- File
                           reader :- (s/=> s/Any File Representation)
                           representations :- RepresentationSets]
  p/Properties
  (properties [_]
    {:allowed-methods #{:get}

     ;; A representation can be given as a parameter, or deduced from
     ;; the filename. The latter is unreliable, as it depends on file
     ;; suffixes.
     :representations (or representations
                          [{:media-type (or (ext-mime-type (.getName file))
                                            "application/octet-stream")}])})

  (properties [_ ctx]
    {:exists? (.exists file)
     :last-modified (Date. (.lastModified file))
     })

  Get
  (GET [_ ctx]
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

    ;; A non-nil reader can be applied to the file, to produce
    ;; another file, string or other body content.

    (assoc (:response ctx)
           :body (if reader
                   (reader file (-> ctx :response :representation))
                   file)))


  Put
  (PUT [_ ctx] (bs/transfer (-> ctx :request :body) file))

  Delete
  (DELETE [_ ctx] (.delete file)))

(defn filename-ext
  "Returns the file extension of a filename or filepath."
  [filename]
  (if-let [ext (second (re-find #"\.([^./\\]+)$" filename))]
    (str/lower-case ext)))

(defn available-media-types [suffix]
  (case suffix
    "md" ["text/html" "text/plain"]))

(defn with-newline [s]
  (str s \newline))

(defn dir-index [dir content-type]
  (assert content-type)

  (case (mt/media-type content-type)
    "text/plain"
    (apply str
           (for [child (sort (.listFiles dir))]
             (str (.getName child) \newline)))

    "text/html"
    (with-newline
      (html
       [:html
        [:head
         [:title (.getName dir)]]
        [:body
         [:table {:style "font-family: monospace"}
          [:thead
           [:tr
            [:th "Name"]
            [:th "Size"]
            [:th "Last modified"]]]
          [:tbody
           (for [child (sort (.listFiles dir))]
             [:tr
              [:td [:a {:href (if (.isDirectory child) (str (.getName child) "/") (.getName child))} (.getName child)]]
              [:td {:style "align: right"}
               (if (.isDirectory child) "" (.length child))]
              [:td (.format
                    (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss zzz")
                      (.setTimeZone (TimeZone/getTimeZone "UTC")))
                    (java.util.Date. (.lastModified child)))]])]]]]))))



(defn dir-properties
  "Return properties of directory"
  [dir ctx]
  {::file dir})

(first (filter #{"README.md" "README.org" "index.html"} ["f" "index.html" "README.org"]))

(s/defrecord DirectoryResource
    [dir :- File
     ;; A map between file suffices and extra args that will be used
     ;; when constructing the FileResource. This enables certain files
     ;; (e.g. markdown, org-mode) to be handled. The reader entry calls
     ;; a function to return the body (arguments are the file and the
     ;; negotiated representation)
     custom-suffices :- (s/maybe {String ; suffix
                                  {:reader (s/=> s/Any File Representation)
                                   :representations RepresentationSets}})
     index-files :- [String]]

  p/Properties
  (properties
   [_]
   {:allowed-methods #{:get}
    :collection? true})

  (properties
   [_ ctx]
   (if-let [path-info (-> ctx :request :path-info)]
     (let [f (io/file dir path-info)
           suffix (filename-ext (.getName f))
           custom-suffix-args (get custom-suffices suffix)]
       (cond
         (and (.isFile f) custom-suffix-args)
         (let [f (map->FileResource (merge custom-suffix-args {:file f}))]
           (merge (p/properties f) {::file f}))

         (.isFile f)
         {:exists? (.exists f)
          :representations [{:media-type (or (ext-mime-type (.getName f))
                                             "application/octet-stream")}]
          :last-modified (.lastModified f)
          ::file f}

         (and (.isDirectory f) (.exists f))
         (if-let [index-file (first (filter (set (seq (.list f))) index-files))]
           (throw
            (ex-info "Redirect"
                     {:status 302
                      :headers {"Location" (str (get-in ctx [:request :uri] ) index-file)}})
            )
           {:exists? true
            :representations [{:media-type #{"text/html" "text/plain;q=0.9"}}]
            :last-modified (.lastModified f)
            ::file f})

         :otherwise
         {:exists? false}))

     {:exists? false}))

  Get
  (GET [this ctx]
       (let [f (get-in ctx [:properties ::file])]
         (assert f)
         (cond
           (instance? FileResource f) (GET f ctx)
           (.isFile f) f
           (.isDirectory f)
           (dir-index f
                      (-> ctx :response :representation :media-type))))))

(defn new-directory-resource [dir opts]
  (map->DirectoryResource (merge opts {:dir dir})))

(extend-protocol p/ResourceCoercion
  File
  (as-resource [f]
    (if (.isDirectory f)
      (map->DirectoryResource {:dir f})
      (map->FileResource {:file f}))))
