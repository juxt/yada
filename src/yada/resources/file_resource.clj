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
   [yada.resource :refer [resource]]
   [yada.protocols :as p]
   [yada.media-type :as mt]
   [yada.schema :refer [Representation]])
  (:import [java.io File]
           [java.nio.file Files]
           [java.nio.file.attribute PosixFileAttributeView PosixFilePermissions]
           [java.util Date TimeZone]
           [java.text SimpleDateFormat]
           [java.nio.charset Charset]))

(defn respond-with-file [ctx file reader]
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
                 ;; FIXME: This requires intimate knowledge of the ctx structure
                 (reader file (-> ctx :response :produces))
                 file)))

(s/defn new-file-resource
  [file :- File
   {:keys [reader produces]}
   :- {(s/optional-key :reader) (s/=> s/Any File Representation)
       (s/optional-key :produces) [Representation]}]

  (resource
   {:produces (or produces
                  [{:media-type (or (ext-mime-type (.getName file))
                                    "application/octet-stream")}])
    :properties (fn [ctx]
                  {
                   ;; A representation can be given as a parameter, or deduced from
                   ;; the filename. The latter is unreliable, as it depends on file
                   ;; suffixes.
                   
                   :exists? (.exists file)
                   :last-modified (Date. (.lastModified file))})

    :methods {:get {:response (fn [ctx]
                                (respond-with-file ctx file reader))}
              :put {:response (fn [ctx]
                                (bs/transfer (-> ctx :request :body) file))}
              
              :delete {:response (fn [ctx] (.delete file))}}}))

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

  (case (:name content-type)
    "text/plain"
    (apply str
           (for [child (sort (.listFiles dir))]
             (str (.getName child) \newline)))

    "text/html"
    (let [path (.toPath dir)]
      (with-newline
        (html
         [:html
          [:head
           [:title (.getName dir)]
           [:style "th {font-family: sans-serif} td {font-family: monospace} td, th {border-bottom: 1px solid #aaa; padding: 4px} a {text-decoration: none} td.monospace {font-family: monospace; }"]]
          
          [:body
           [:table {:style "border-spacing: 14px; border: 1px solid black; border-collapse: collapse"}
            #_[:thead
             [:tr
              [:th "Permissions"]
              [:th "Owner"]
              [:th "Size"]
              [:th "Last modified"]
              [:th "Name"]]]
            [:tbody
             (for [child (sort (.listFiles dir))
                   :let [path (.toPath child)
                         attrs (.readAttributes (Files/getFileAttributeView path PosixFileAttributeView (into-array java.nio.file.LinkOption [])))]]
               [:tr
                [:td.monospace (str (if (Files/isDirectory path (into-array java.nio.file.LinkOption [])) "d" "-") (PosixFilePermissions/toString (.permissions attrs)))]
                [:td
                 (.getName (.owner attrs))
                 ]
                [:td.monospace {:style "text-align: right"}
                 (if (.isDirectory child) "" (.length child))]
                [:td.monospace [:small (.format
                                        (doto (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss zzz")
                                          (.setTimeZone (TimeZone/getTimeZone "UTC")))
                                        (java.util.Date. (.lastModified child)))]]
                [:td (let [href (if (.isDirectory child) (str (.getName child) "/") (.getName child))]
                       [:a {:href href} (.getName child)])]])]]]])))))

(defn dir-properties
  "Return properties of directory"
  [dir ctx]
  {::file dir})

(defn- maybe-redirect-to-index [dir req index-files]
  (when-let [index-file (first (filter (set (seq (.list dir))) index-files))]
    (throw
     (ex-info "Redirect"
              {:status 302 :headers {"Location" (str (:uri req) index-file)}}))))

(s/defn new-directory-resource
  [dir :- File
   ;; A map between file suffices and extra args that will be used
   ;; when constructing the FileResource. This enables certain files
   ;; (e.g. markdown, org-mode) to be handled. The reader entry calls
   ;; a function to return the body (arguments are the file and the
   ;; negotiated representation)
   {:keys [custom-suffices index-files]}
   :- {(s/optional-key :custom-suffices)
       {String                          ; suffix
        {(s/optional-key :reader) (s/=> s/Any File Representation)
         :produces [Representation]}}
       (s/optional-key :index-files) [String]}]

  (resource
   {:path-info? true
    :produces "text/html"
    :methods {:get (fn [ctx]
                     (infof "here!")
                     (or (maybe-redirect-to-index dir (:request ctx) index-files)
                         (dir-index dir (-> ctx :response :produces :media-type))))}
    :subresource
    (fn [ctx]
      (infof "subresource")
      (let [f (io/file dir (-> ctx :request :path-info))
            suffix (filename-ext (.getName f))
            custom-suffix-args (get custom-suffices suffix)]
        (cond
          (.isFile f)
          (resource
           {:properties {:exists? (.exists f)
                         :last-modified (Date. (.lastModified f))}
            :produces (if custom-suffix-args
                        (:produces custom-suffix-args)
                        [{:media-type (or (ext-mime-type (.getName f)) "application/octet-stream")}])
            :methods
            {:get (fn [ctx] (respond-with-file ctx f (some-> custom-suffix-args :reader)))}})

          (and (.isDirectory f) (.exists f))
          (or
           (maybe-redirect-to-index f (:request ctx) index-files)
           (resource {:produces [{:media-type #{"text/html" "text/plain;q=0.9"}}]
                      :properties {:last-modified (Date. (.lastModified f))}
                      :methods {:get (fn [ctx] (dir-index f (-> ctx :response :produces :media-type)))}}))

          :otherwise (p/as-resource nil))))}))

(extend-protocol p/ResourceCoercion
  File
  (as-resource [f]
    (if (.isDirectory f)
      (new-directory-resource f {})
      (new-file-resource f {})
      )))
