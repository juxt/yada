;; Copyright © 2015, JUXT LTD.

(ns yada.resources.file-resource
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :refer :all]
            [hiccup.core :refer (html h)]
            [ring.util.mime-type :refer (ext-mime-type)]
            [ring.util.response :refer (redirect)]
            [ring.util.time :refer (format-date)]
            [yada.resource :refer [ResourceAllowedMethods ResourceExistence ResourceModification ResourceRepresentations ResourceFetch ResourceCoercion representations platform-charsets]]
            [yada.representation :as rep]
            [yada.methods :refer (Get GET Put PUT Post POST Delete DELETE)]
            [yada.negotiation :as negotiation]
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

(defn with-newline [s]
  (str s \newline))

(defn dir-index [dir content-type]
  (assert content-type)
  (case (mime/media-type content-type)
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
                    (java.util.Date. (.lastModified child)))]])]]]]))))

(defn negotiate-file-info [f ctx]
  (let [neg
        (negotiation/interpret-negotiation
         (first (negotiation/negotiate
                 (negotiation/extract-request-info (:request ctx))
                 (negotiation/coerce-representations
                  [{:content-type (set (remove nil? [(ext-mime-type (.getName f))]))}]))))]
    (when-let [status (:status neg)]
      (throw (ex-info "" {:status status :yada.core/http-response true})))
    neg))

(defrecord FileResource [f]
  ResourceAllowedMethods
  (allowed-methods [_] #{:get :head :put :delete})

  ResourceExistence
  (exists? [_ ctx] (.exists f))

  ResourceModification
  (last-modified [_ ctx] (Date. (.lastModified f)))

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

    (if (.exists f)
      ;; We need to return the file, but also we need to neg content-type
      (let [neg (negotiate-file-info f ctx)]
        (cond-> (:response ctx)
          f (assoc :body f)
          neg (assoc-in [:response :representation] neg)))

      ;; Otherwise if file doesn't exist
      (throw (ex-info "Not found" {:status 404 :yada.core/http-response true}))))

  Put
  (PUT [_ ctx] (bs/transfer (-> ctx :request :body) f))

  Delete
  (DELETE [_ ctx] (.delete f))

  ResourceRepresentations
  (representations [_]
    [{:method #{:get :head}
      :content-type (set (remove nil? [(ext-mime-type (.getName f))]))}]))

(defrecord DirectoryResource [dir]
  ResourceAllowedMethods
  (allowed-methods [_] #{:get :head :put :delete})

  ResourceExistence
  (exists? [_ ctx] (.exists dir))

  ResourceModification
  (last-modified [_ ctx] (Date. (.lastModified dir)))

  ResourceRepresentations
  (representations [_]
    ;; For when path-info is nil
    [{:method #{:get :head}
      :content-type #{"text/html" "text/plain"}
      :charset platform-charsets}])

  Get
  (GET [this ctx]
    (if-let [path-info (-> ctx :request :path-info)]
      (if (= path-info "")
        (let [neg (negotiation/interpret-negotiation
                   (first (negotiation/negotiate
                           (negotiation/extract-request-info (:request ctx))
                           (negotiation/coerce-representations (representations this)))))
              ct (:content-type neg)]

          (cond-> (:response ctx)
            true (assoc :body (rep/to-body (dir-index dir ct) neg))
            neg (assoc :representation neg)))

        (let [f (child-file dir path-info)]
          (cond

            (.isFile f)
            ;; If it's a file, we must negotiate the content-type
            (let [neg (negotiate-file-info f ctx)]
              (cond-> (:response ctx)
                f (assoc :body f)
                (:content-type neg) (assoc :content-type (:content-type neg))))

            (.isDirectory f)
            ;; This is sub-directory, with path-info, so no
            ;; negotiation has been done (see yada.core which explains
            ;; why negotiation is not done when there's a path-info in
            ;; the request)
            (let [neg (negotiation/interpret-negotiation
                       (first (negotiation/negotiate
                               (negotiation/extract-request-info (:request ctx))
                               (negotiation/coerce-representations (representations this)))))
                  ct (:content-type neg)]
              (cond-> (:response ctx)
                true (assoc :body (rep/to-body (dir-index f ct) neg))
                neg (assoc :representation neg)))

            :otherwise
            (throw (ex-info "File not found" {:status 404 :yada.core/http-response true})))))

      ;; Redirect so that path-info is not nil - there is a case for this being done in the bidi handler
      (throw (ex-info "" {:status 302 :headers {"location" (str (-> ctx :request :uri) "/")}
                          :yada.core/http-response true}))))


  Put
  (PUT [_ ctx]
    (if-let [path-info (-> ctx :request :path-info)]
      (let [f (child-file dir path-info)]
        (bs/transfer (-> ctx :request :body) f))
      (throw (ex-info "TODO: Directory creation from archive stream is not yet implemented" {}))))


  Delete
  (DELETE [_ ctx]
    (do
      (let [path-info (-> ctx :request :path-info)]
        ;; TODO: We must be ensure that the path-info points to a file
        ;; within the directory tree, otherwise this is an attack vector -
        ;; we should return 403 in this case - same above with PUTs and POSTs

        (if (= path-info "")
          (if (seq (.listFiles dir))
            (throw (ex-info "By default, the policy is not to delete a non-empty directory" {:files (.listFiles dir)}))
            (io/delete-file dir))

          (let [f (child-file dir path-info)]
            ;; f is not certain to exist
            (if (.exists f)
              (.delete f)
              (throw (ex-info {:status 404 :yada.core/http-response true})))))))))

(extend-protocol ResourceCoercion
  File
  (make-resource [f]
    (if (.isDirectory f)
      (->DirectoryResource f)
      (->FileResource f))))
