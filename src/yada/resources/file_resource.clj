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
   [yada.protocols :as p]
   [yada.methods :refer (Get GET Put PUT Post POST Delete DELETE)]
   [yada.media-type :as mt])
  (:import [java.io File]
           [java.util Date TimeZone]
           [java.text SimpleDateFormat]
           [java.nio.charset Charset]))

(defrecord FileResource [f]
  p/ResourceProperties
  (resource-properties [_]
    {:allowed-methods #{:get :put :delete}
     ;; TODO: It may be desirable to 'coerce' files according to client
     ;; needs. For example, a README.md can be provided as text/html and
     ;; converted by the server.
     :representations [{:media-type (or (ext-mime-type (.getName f)) "application/octet-stream")}]})

  (resource-properties [_ ctx]
    {:exists? (.exists f)
     :last-modified (Date. (.lastModified f))
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

    (assoc (:response ctx) :body f))

  Put
  (PUT [_ ctx] (bs/transfer (-> ctx :request :body) f))

  Delete
  (DELETE [_ ctx] (.delete f)))

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

(defn dir-index [dir indices content-type]
  (assert content-type)
  (infof "Indices is %s" (pr-str indices))
  (infof "content-type is %s" (pr-str content-type))

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

(defn dir-resource-properties [dir index-files]

  ;; Look for a possible index file, (. dir list)

  ;; If one matches, use it to construct a resource

  (let [indices (filter #{"README.md"} (seq (. dir list)))]
    {:exists? true
     :representations [{:media-type
                        (set (sequence (comp (map filename-ext)
                                             (mapcat available-media-types))
                                       indices))}]
     :last-modified (.lastModified dir)
     ::file dir
     ::indices indices}))

(s/defrecord DirectoryResource
    [dir :- File
     index-files :- (s/maybe [java.util.regex.Pattern])]
  p/ResourceProperties
  (resource-properties [_]
    {:allowed-methods #{:get}
     :collection? true})

  (resource-properties [_ ctx]
    (if-let [path-info (-> ctx :request :path-info)]
      (let [f (io/file dir path-info)]
        (cond
          (.isFile f)
          {:exists? (.exists f)
           :representations [{:media-type (or (ext-mime-type (.getName f))
                                              "application/octet-stream")}]
           :last-modified (.lastModified f)
           ::file f}

          (.isDirectory f)
          (dir-resource-properties f index-files)

          :otherwise
          {:exists? false}))

      {:exists? false}))

  Get
  (GET [this ctx]
    (let [f (get-in ctx [:resource-properties ::file])]
      (cond
        (.isFile f) f
        (.isDirectory f)
        (dir-index f
                   (get-in ctx [:resource-properties ::indices])
                   (-> ctx :response :representation :media-type))))))

(defn new-directory-resource [dir opts]
  (map->DirectoryResource (merge opts {:dir dir})))

(extend-protocol p/ResourceCoercion
  File
  (as-resource [f]
    (if (.isDirectory f)
      (map->DirectoryResource {:dir f})
      (->FileResource f))))
